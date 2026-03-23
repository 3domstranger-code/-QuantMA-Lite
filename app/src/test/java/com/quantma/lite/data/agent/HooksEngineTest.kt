package com.quantma.lite.data.agent

import com.quantma.lite.data.local.db.HookDao
import com.quantma.lite.data.local.db.entity.HookEntity
import com.quantma.lite.domain.model.HookTrigger
import com.quantma.lite.domain.model.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HooksEngineTest {

    // ─── Fake HookDao ────────────────────────────────────────────────────────

    private class FakeHookDao(
        private var hooks: List<HookEntity> = emptyList()
    ) : HookDao {
        override fun observeAll(): Flow<List<HookEntity>> = flowOf(hooks)
        override suspend fun getEnabledByTrigger(trigger: String): List<HookEntity> =
            hooks.filter { it.isEnabled && it.trigger == trigger }
        override suspend fun getCount(): Int = hooks.size
        override suspend fun insert(hook: HookEntity): Long {
            hooks = hooks + hook
            return hooks.size.toLong()
        }
        override suspend fun update(hook: HookEntity) {}
        override suspend fun delete(hook: HookEntity) {}
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
    }

    // ─── fireHooks ───────────────────────────────────────────────────────────

    @Test
    fun `fireHooks returns combined actions for matching trigger`() = runBlocking {
        val dao = FakeHookDao(listOf(
            HookEntity(id = 1, trigger = "PRE_COMMIT", action = "Review staged changes", isEnabled = true),
            HookEntity(id = 2, trigger = "PRE_COMMIT", action = "Check for debug code", isEnabled = true)
        ))
        val engine = HooksEngine(dao)

        val result = engine.fireHooks(HookTrigger.PRE_COMMIT)

        assertTrue("Should contain first action", result.contains("Review staged changes"))
        assertTrue("Should contain second action", result.contains("Check for debug code"))
        assertTrue("Should have HOOK prefix", result.contains("HOOK (PRE_COMMIT):"))
    }

    @Test
    fun `fireHooks with no matching hooks returns empty string`() = runBlocking {
        val dao = FakeHookDao(listOf(
            HookEntity(id = 1, trigger = "POST_COMMIT", action = "Post-commit action", isEnabled = true)
        ))
        val engine = HooksEngine(dao)

        val result = engine.fireHooks(HookTrigger.PRE_COMMIT)

        assertEquals("No matching hooks should return empty", "", result)
    }

    @Test
    fun `fireHooks ignores disabled hooks`() = runBlocking {
        val dao = FakeHookDao(listOf(
            HookEntity(id = 1, trigger = "PRE_COMMIT", action = "Disabled action", isEnabled = false)
        ))
        val engine = HooksEngine(dao)

        val result = engine.fireHooks(HookTrigger.PRE_COMMIT)

        assertEquals("Disabled hooks should not fire", "", result)
    }

    // ─── getTriggerForToolCall ────────────────────────────────────────────────

    @Test
    fun `getTriggerForToolCall maps GitCommit to PRE_COMMIT`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getTriggerForToolCall(ToolCall.GitCommit("/repo", "msg"))

        assertEquals(HookTrigger.PRE_COMMIT, trigger)
    }

    @Test
    fun `getTriggerForToolCall maps WriteFile to ON_FILE_CHANGE`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getTriggerForToolCall(ToolCall.WriteFile("/file.kt", "content"))

        assertEquals(HookTrigger.ON_FILE_CHANGE, trigger)
    }

    @Test
    fun `getTriggerForToolCall returns null for ReadFile`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getTriggerForToolCall(ToolCall.ReadFile("/file.kt"))

        assertNull("ReadFile should not trigger any hook", trigger)
    }

    @Test
    fun `getTriggerForToolCall returns null for ListFiles`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getTriggerForToolCall(ToolCall.ListFiles("/dir"))

        assertNull("ListFiles should not trigger any hook", trigger)
    }

    // ─── getPostTriggerForToolCall ────────────────────────────────────────────

    @Test
    fun `getPostTriggerForToolCall maps GitCommit to POST_COMMIT`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getPostTriggerForToolCall(ToolCall.GitCommit("/repo", "msg"))

        assertEquals(HookTrigger.POST_COMMIT, trigger)
    }

    @Test
    fun `getPostTriggerForToolCall returns null for WriteFile`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getPostTriggerForToolCall(ToolCall.WriteFile("/file.kt", "content"))

        assertNull("WriteFile should not have post trigger", trigger)
    }

    @Test
    fun `getPostTriggerForToolCall returns null for ReadFile`() {
        val dao = FakeHookDao()
        val engine = HooksEngine(dao)

        val trigger = engine.getPostTriggerForToolCall(ToolCall.ReadFile("/file.kt"))

        assertNull("ReadFile should not have post trigger", trigger)
    }

    // ─── seedDefaultHooks ────────────────────────────────────────────────────

    @Test
    fun `seedDefaultHooks only runs when count is 0`() = runBlocking {
        val dao = FakeHookDao(listOf(
            HookEntity(id = 1, trigger = "PRE_COMMIT", action = "existing", isEnabled = true)
        ))
        val engine = HooksEngine(dao)
        val countBefore = dao.getCount()

        engine.seedDefaultHooks()

        assertEquals("Should not seed when hooks already exist", countBefore, dao.getCount())
    }

    @Test
    fun `seedDefaultHooks inserts hooks when empty`() = runBlocking {
        val dao = FakeHookDao(emptyList())
        val engine = HooksEngine(dao)

        engine.seedDefaultHooks()

        assertTrue("Should have seeded hooks", dao.getCount() > 0)
    }
}
