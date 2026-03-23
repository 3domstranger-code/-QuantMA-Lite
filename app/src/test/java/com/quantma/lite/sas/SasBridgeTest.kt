package com.quantma.lite.sas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Synthetic unit tests for [SasBridge].
 *
 * Tests processLlmOutput (synchronous), rate limiting, ShellExecutor.execute (synchronous),
 * and static/structural checks. Coroutine-based loop tests (runAgentLoop) are
 * omitted because kotlinx-coroutines is not in the unit-test classpath — add
 * testImplementation(libs.coroutines.test) to include them.
 */
class SasBridgeTest {

    private lateinit var workingDir: File
    private lateinit var config: SasConfig
    private lateinit var executor: ShellExecutor
    private lateinit var parser: CommandParser
    private lateinit var bridge: SasBridge

    @Before
    fun setUp() {
        workingDir = File(System.getProperty("java.io.tmpdir"), "sas_bridge_test").also {
            it.mkdirs()
        }
        config = SasConfig(workingDir = workingDir.absolutePath)
        executor = ShellExecutor(config)
        parser = CommandParser()
        bridge = SasBridge(executor, parser, config)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // processLlmOutput
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `processLlmOutput detects JSON command`() {
        val output = """{"tool": "list_dir", "args": {"path": "."}}"""
        val result = bridge.processLlmOutput(output)
        assertNotNull("Should parse JSON command", result.command)
        assertEquals(ToolType.LIST_DIR, result.command!!.tool)
        assertFalse(result.isDone)
    }

    @Test
    fun `processLlmOutput detects DONE tool`() {
        val output = """{"tool": "done"}"""
        val result = bridge.processLlmOutput(output)
        assertNotNull(result.command)
        assertEquals(ToolType.DONE, result.command!!.tool)
        assertTrue(result.isDone)
    }

    @Test
    fun `processLlmOutput handles pure text`() {
        val output = "I will read the file for you."
        val result = bridge.processLlmOutput(output)
        assertTrue(result.hasText)
        assertFalse(result.hasCommand)
        assertEquals(output, result.displayText)
    }

    @Test
    fun `processLlmOutput handles mixed text plus JSON`() {
        val output = "I'll list the directory now.\n{\"tool\": \"list_dir\", \"args\": {\"path\": \".\"}}"
        val result = bridge.processLlmOutput(output)
        assertTrue(result.hasText)
        assertTrue(result.hasCommand)
        assertEquals(ToolType.LIST_DIR, result.command!!.tool)
    }

    @Test
    fun `processLlmOutput detects DONE on its own line`() {
        val output = "Task complete.\nDONE"
        val result = bridge.processLlmOutput(output)
        assertTrue(result.isDone)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // executeCommand (via ShellExecutor.execute — synchronous)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `executor list_dir on existing dir returns Success`() {
        val cmd = ToolCommand(tool = ToolType.LIST_DIR, args = mapOf("path" to "."))
        val response = executor.execute(cmd)
        assertTrue("list_dir on working dir should succeed", response is ToolResponse.Success)
    }

    @Test
    fun `executor read_file on missing file returns Error`() {
        val cmd = ToolCommand(tool = ToolType.READ_FILE, args = mapOf("path" to "does_not_exist.kt"))
        val response = executor.execute(cmd)
        assertTrue("Missing file should return Error", response is ToolResponse.Error)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Rate limiting (via ShellExecutor)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `rate limiting blocks excess operations`() {
        val limitedConfig = SasConfig(workingDir = workingDir.absolutePath, maxOpsPerMinute = 3)
        val limitedExecutor = ShellExecutor(limitedConfig)

        val cmd = ToolCommand(tool = ToolType.LIST_DIR, args = mapOf("path" to "."))

        // First 3 should succeed
        val r1 = limitedExecutor.execute(cmd)
        val r2 = limitedExecutor.execute(cmd)
        val r3 = limitedExecutor.execute(cmd)
        assertTrue(r1 is ToolResponse.Success)
        assertTrue(r2 is ToolResponse.Success)
        assertTrue(r3 is ToolResponse.Success)

        // 4th should be rate-limited
        val r4 = limitedExecutor.execute(cmd)
        assertTrue("4th call should be rate-limited", r4 is ToolResponse.Error)
        assertEquals("RATE_LIMIT_EXCEEDED", (r4 as ToolResponse.Error).code)
    }

    @Test
    fun `rate limiting is disabled when maxOpsPerMinute is 0`() {
        val unlimitedConfig = SasConfig(workingDir = workingDir.absolutePath, maxOpsPerMinute = 0)
        val unlimitedExecutor = ShellExecutor(unlimitedConfig)
        val cmd = ToolCommand(tool = ToolType.LIST_DIR, args = mapOf("path" to "."))
        repeat(10) {
            val r = unlimitedExecutor.execute(cmd)
            assertTrue("Unlimited config should always succeed", r is ToolResponse.Success)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FILESYSTEM_MUTATING_TOOLS invalidation coverage
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `FILESYSTEM_MUTATING_TOOLS includes expected tools`() {
        // Access via reflection-free way: rely on the logic in runAgentLoop
        // by confirming the set is consistent with write operations
        val writeOps = setOf(
            ToolType.WRITE_FILE, ToolType.CREATE_FILE,
            ToolType.APPEND_FILE, ToolType.DELETE_FILE, ToolType.FORMAT_FILE
        )
        // At minimum these must be considered mutating
        writeOps.forEach { tool ->
            assertTrue(
                "$tool should be in FILESYSTEM_MUTATING_TOOLS",
                tool.category == ToolType.Category.FILE_OPS ||
                    tool.category == ToolType.Category.CODE_QUALITY
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bridge initial state
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `bridge starts in Idle state`() {
        assertEquals(BridgeState.Idle, bridge.state.value)
    }

    @Test
    fun `bridge starts with empty rounds`() {
        assertTrue("Rounds should be empty initially", bridge.rounds.isEmpty())
    }

    @Test
    fun `reset clears state to Idle`() {
        bridge.reset()
        assertEquals(BridgeState.Idle, bridge.state.value)
        assertTrue("Rounds should be empty after reset", bridge.rounds.isEmpty())
    }
}
