package com.quantma.lite.data.agent

import com.quantma.lite.data.local.db.RuleDao
import com.quantma.lite.data.local.db.entity.RuleEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RulesEngineTest {

    // ─── Fake RuleDao ────────────────────────────────────────────────────────

    private class FakeRuleDao(
        private var rules: List<RuleEntity> = emptyList()
    ) : RuleDao {
        override fun observeAll(): Flow<List<RuleEntity>> = flowOf(rules)
        override suspend fun getEnabled(): List<RuleEntity> = rules.filter { it.isEnabled }
        override suspend fun getCount(): Int = rules.size
        override suspend fun insert(rule: RuleEntity): Long {
            rules = rules + rule
            return rules.size.toLong()
        }
        override suspend fun update(rule: RuleEntity) {}
        override suspend fun delete(rule: RuleEntity) {}
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
    }

    // ─── buildRulesBlock ─────────────────────────────────────────────────────

    @Test
    fun `buildRulesBlock with enabled rules returns formatted text`() = runBlocking {
        val dao = FakeRuleDao(listOf(
            RuleEntity(
                id = 1,
                category = "CODE_STYLE",
                instruction = "Use meaningful names",
                isEnabled = true
            )
        ))
        val engine = RulesEngine(dao)

        val result = engine.buildRulesBlock()

        assertTrue("Should contain header", result.contains("# Code Quality Rules"))
        assertTrue("Should contain category", result.contains("## Code Style"))
        assertTrue("Should contain instruction", result.contains("- Use meaningful names"))
    }

    @Test
    fun `buildRulesBlock with no rules returns empty string`() = runBlocking {
        val dao = FakeRuleDao(emptyList())
        val engine = RulesEngine(dao)

        val result = engine.buildRulesBlock()

        assertEquals("Empty rules should return empty string", "", result)
    }

    @Test
    fun `buildRulesBlock with disabled rules returns empty string`() = runBlocking {
        val dao = FakeRuleDao(listOf(
            RuleEntity(
                id = 1,
                category = "CODE_STYLE",
                instruction = "Should not appear",
                isEnabled = false
            )
        ))
        val engine = RulesEngine(dao)

        val result = engine.buildRulesBlock()

        assertEquals("Disabled rules only should return empty string", "", result)
    }

    @Test
    fun `buildRulesBlock groups rules by category`() = runBlocking {
        val dao = FakeRuleDao(listOf(
            RuleEntity(id = 1, category = "CODE_STYLE", instruction = "Style rule 1", isEnabled = true),
            RuleEntity(id = 2, category = "SECURITY", instruction = "Security rule 1", isEnabled = true),
            RuleEntity(id = 3, category = "CODE_STYLE", instruction = "Style rule 2", isEnabled = true),
            RuleEntity(id = 4, category = "ARCHITECTURE", instruction = "Arch rule 1", isEnabled = true)
        ))
        val engine = RulesEngine(dao)

        val result = engine.buildRulesBlock()

        assertTrue("Should contain Code Style category", result.contains("## Code Style"))
        assertTrue("Should contain Security category", result.contains("## Security"))
        assertTrue("Should contain Architecture category", result.contains("## Architecture"))
        assertTrue("Should list style rule 1", result.contains("- Style rule 1"))
        assertTrue("Should list style rule 2", result.contains("- Style rule 2"))
        assertTrue("Should list security rule", result.contains("- Security rule 1"))
        assertTrue("Should list arch rule", result.contains("- Arch rule 1"))
    }

    @Test
    fun `buildRulesBlock formats COMMENTS category correctly`() = runBlocking {
        val dao = FakeRuleDao(listOf(
            RuleEntity(
                id = 1,
                category = "COMMENTS",
                instruction = "Document public APIs",
                isEnabled = true
            )
        ))
        val engine = RulesEngine(dao)

        val result = engine.buildRulesBlock()

        assertTrue("COMMENTS should format as 'Comments & Documentation'",
            result.contains("## Comments & Documentation"))
    }

    @Test
    fun `buildRulesBlock passes through unknown category as-is`() = runBlocking {
        val dao = FakeRuleDao(listOf(
            RuleEntity(
                id = 1,
                category = "CUSTOM_CATEGORY",
                instruction = "Custom rule",
                isEnabled = true
            )
        ))
        val engine = RulesEngine(dao)

        val result = engine.buildRulesBlock()

        assertTrue("Unknown category should appear as-is",
            result.contains("## CUSTOM_CATEGORY"))
    }

    // ─── seedDefaultRules ────────────────────────────────────────────────────

    @Test
    fun `seedDefaultRules only runs when count is 0`() = runBlocking {
        val dao = FakeRuleDao(listOf(
            RuleEntity(id = 1, category = "X", instruction = "existing", isEnabled = true)
        ))
        val engine = RulesEngine(dao)
        val countBefore = dao.getCount()

        engine.seedDefaultRules()

        assertEquals("Should not seed when rules already exist", countBefore, dao.getCount())
    }

    @Test
    fun `seedDefaultRules inserts rules when empty`() = runBlocking {
        val dao = FakeRuleDao(emptyList())
        val engine = RulesEngine(dao)

        engine.seedDefaultRules()

        assertTrue("Should have seeded rules", dao.getCount() > 0)
    }
}
