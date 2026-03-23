package com.quantma.lite.sas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HookManagerTest {

    private lateinit var manager: HookManager

    @Before
    fun setUp() {
        manager = HookManager()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Registration
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `addHook registers hook`() {
        manager.addHook(buildPostWriteFormatHook())
        assertTrue(manager.hasHooks(ToolType.WRITE_FILE))
    }

    @Test
    fun `clearAll removes all hooks`() {
        manager.addHook(buildPostWriteFormatHook())
        manager.clearAll()
        assertFalse(manager.hasHooks(ToolType.WRITE_FILE))
    }

    @Test
    fun `removeHook by tool and type removes matching hooks`() {
        manager.addHook(buildPostWriteFormatHook())
        manager.removeHook(ToolType.WRITE_FILE, HookType.POST_COMMAND)
        assertFalse(manager.hasHooks(ToolType.WRITE_FILE))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Lookup (without conditions)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `getPreHooks returns only pre-command hooks`() {
        manager.addHook(buildPostWriteFormatHook())
        manager.addHook(HookConfig(
            hookType = HookType.PRE_COMMAND,
            triggerTool = ToolType.WRITE_FILE,
            hookAction = ToolCommand(tool = ToolType.LINT_FILE, args = mapOf("path" to "\$TARGET_PATH")),
            description = "Pre-lint"
        ))
        val pre = manager.getPreHooks(ToolType.WRITE_FILE)
        assertEquals(1, pre.size)
        assertEquals(ToolType.LINT_FILE, pre[0].hookAction.tool)
    }

    @Test
    fun `getPostHooks returns only post-command hooks`() {
        manager.addHook(buildPostWriteFormatHook())
        val post = manager.getPostHooks(ToolType.WRITE_FILE)
        assertEquals(1, post.size)
        assertEquals(ToolType.FORMAT_FILE, post[0].hookAction.tool)
    }

    @Test
    fun `disabled hooks are excluded from lookup`() {
        manager.addHook(buildPostWriteFormatHook().copy(enabled = false))
        val post = manager.getPostHooks(ToolType.WRITE_FILE)
        assertTrue(post.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Priority Ordering
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `getPostHooks returns hooks sorted by priority ascending`() {
        manager.addHook(buildPostWriteFormatHook().copy(priority = 10, description = "low"))
        manager.addHook(buildPostWriteFormatHook().copy(
            hookAction = ToolCommand(tool = ToolType.LINT_FILE, args = mapOf("path" to "\$TARGET_PATH")),
            priority = 1,
            description = "high"
        ))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE)
        assertEquals("high-priority hook should come first", 1, hooks[0].priority)
        assertEquals(10, hooks[1].priority)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Placeholder Resolution
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `resolveHookPlaceholders replaces TARGET_PATH`() {
        val trigger = ToolCommand(
            tool = ToolType.WRITE_FILE,
            args = mapOf("path" to "src/main.kt", "content" to "...")
        )
        val hookAction = ToolCommand(
            tool = ToolType.FORMAT_FILE,
            args = mapOf("path" to "\$TARGET_PATH")
        )
        val resolved = manager.resolveHookPlaceholders(hookAction, trigger)
        assertEquals("src/main.kt", resolved.args["path"])
    }

    @Test
    fun `resolveHookPlaceholders replaces TARGET_CONTENT`() {
        val trigger = ToolCommand(
            tool = ToolType.WRITE_FILE,
            args = mapOf("path" to "file.txt", "content" to "hello world")
        )
        val hookAction = ToolCommand(
            tool = ToolType.LINT_FILE,
            args = mapOf("content" to "\$TARGET_CONTENT")
        )
        val resolved = manager.resolveHookPlaceholders(hookAction, trigger)
        assertEquals("hello world", resolved.args["content"])
    }

    @Test
    fun `resolveHookPlaceholders leaves non-placeholder args unchanged`() {
        val trigger = ToolCommand(
            tool = ToolType.WRITE_FILE,
            args = mapOf("path" to "file.kt")
        )
        val hookAction = ToolCommand(
            tool = ToolType.FORMAT_FILE,
            args = mapOf("path" to "\$TARGET_PATH", "strict" to "true")
        )
        val resolved = manager.resolveHookPlaceholders(hookAction, trigger)
        assertEquals("file.kt", resolved.args["path"])
        assertEquals("true", resolved.args["strict"])
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Conditional Hooks — PathExtension
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `PathExtension condition fires for matching extension`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.PathExtension(setOf("kt", "kts"))
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "src/Main.kt"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger)
        assertEquals(1, hooks.size)
    }

    @Test
    fun `PathExtension condition skips for non-matching extension`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.PathExtension(setOf("kt", "kts"))
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "script.py"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger)
        assertTrue(hooks.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Conditional Hooks — PathPattern
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `PathPattern condition fires for matching path`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.PathPattern(Regex("src/.*\\.kt$"))
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "src/Main.kt"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger)
        assertEquals(1, hooks.size)
    }

    @Test
    fun `PathPattern condition skips for non-matching path`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.PathPattern(Regex("src/.*\\.kt$"))
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "test/Main.kt"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger)
        assertTrue(hooks.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Conditional Hooks — OutputContains
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `OutputContains condition fires when output contains substring`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.OutputContains("WARNING")
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "a.kt"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger, lastOutput = "1 WARNING found")
        assertEquals(1, hooks.size)
    }

    @Test
    fun `OutputContains condition skips when output does not match`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.OutputContains("WARNING")
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "a.kt"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger, lastOutput = "No issues found")
        assertTrue(hooks.isEmpty())
    }

    @Test
    fun `OutputContains is case-insensitive by default`() {
        val hook = buildPostWriteFormatHook().copy(
            condition = HookCondition.OutputContains("warning")
        )
        manager.addHook(hook)
        val trigger = ToolCommand(tool = ToolType.WRITE_FILE, args = mapOf("path" to "a.kt"))
        val hooks = manager.getPostHooks(ToolType.WRITE_FILE, trigger, lastOutput = "1 WARNING found")
        assertEquals(1, hooks.size)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Serialization
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `toConfigList and loadFrom round-trip`() {
        manager.addHook(buildPostWriteFormatHook())
        val saved = manager.toConfigList()

        val restored = HookManager()
        restored.loadFrom(saved)
        assertTrue(restored.hasHooks(ToolType.WRITE_FILE))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Summary
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `summary includes hook details`() {
        manager.addHook(buildPostWriteFormatHook().copy(
            condition = HookCondition.PathExtension(setOf("kt")),
            priority = 5
        ))
        val summary = manager.summary()
        assertTrue(summary.contains("write_file"))
        assertTrue(summary.contains("format_file"))
        assertTrue(summary.contains("kt"))
        assertTrue(summary.contains("p=5"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun buildPostWriteFormatHook() = HookConfig(
        hookType = HookType.POST_COMMAND,
        triggerTool = ToolType.WRITE_FILE,
        hookAction = ToolCommand(
            tool = ToolType.FORMAT_FILE,
            args = mapOf("path" to "\$TARGET_PATH")
        ),
        description = "Auto-format after write"
    )
}
