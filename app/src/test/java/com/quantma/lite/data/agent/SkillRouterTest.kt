package com.quantma.lite.data.agent

import com.quantma.lite.data.local.db.SkillDao
import com.quantma.lite.data.local.db.entity.SkillEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SkillRouterTest {

    // ─── Fake SkillDao ───────────────────────────────────────────────────────

    private class FakeSkillDao(
        private var skills: List<SkillEntity> = emptyList()
    ) : SkillDao {
        override fun observeAll(): Flow<List<SkillEntity>> = flowOf(skills)
        override suspend fun getEnabled(): List<SkillEntity> = skills.filter { it.isEnabled }
        override suspend fun getCount(): Int = skills.size
        override suspend fun insert(skill: SkillEntity): Long {
            skills = skills + skill
            return skills.size.toLong()
        }
        override suspend fun update(skill: SkillEntity) {}
        override suspend fun delete(skill: SkillEntity) {}
        override suspend fun setEnabled(id: Long, enabled: Boolean) {}
    }

    // ─── matchSkills ─────────────────────────────────────────────────────────

    @Test
    fun `matchSkills with keyword match returns skill prompt`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(
                id = 1,
                name = "git_commit",
                triggerPatterns = "commit,git commit",
                promptInjection = "Write clear commit messages",
                isEnabled = true
            )
        ))
        val router = SkillRouter(dao)

        val result = router.matchSkills("I need to commit my changes")

        assertTrue("Should contain skill name header", result.contains("# Skill: git_commit"))
        assertTrue("Should contain prompt injection", result.contains("Write clear commit messages"))
    }

    @Test
    fun `matchSkills with no match returns empty string`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(
                id = 1,
                name = "git_commit",
                triggerPatterns = "commit,git commit",
                promptInjection = "Write clear commit messages",
                isEnabled = true
            )
        ))
        val router = SkillRouter(dao)

        val result = router.matchSkills("What is the weather today?")

        assertEquals("No match should return empty string", "", result)
    }

    @Test
    fun `matchSkills with multiple patterns matches any`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(
                id = 1,
                name = "explain_code",
                triggerPatterns = "explain,what does,how does",
                promptInjection = "Start with a high-level overview",
                isEnabled = true
            )
        ))
        val router = SkillRouter(dao)

        val result1 = router.matchSkills("explain this function")
        assertTrue("Should match 'explain'", result1.contains("# Skill: explain_code"))

        val result2 = router.matchSkills("what does this code do?")
        assertTrue("Should match 'what does'", result2.contains("# Skill: explain_code"))

        val result3 = router.matchSkills("how does the parser work?")
        assertTrue("Should match 'how does'", result3.contains("# Skill: explain_code"))
    }

    @Test
    fun `matchSkills with multiple skills returns all matches`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(
                id = 1,
                name = "git_commit",
                triggerPatterns = "commit",
                promptInjection = "Commit instructions",
                isEnabled = true
            ),
            SkillEntity(
                id = 2,
                name = "code_refactor",
                triggerPatterns = "refactor,commit",
                promptInjection = "Refactor instructions",
                isEnabled = true
            )
        ))
        val router = SkillRouter(dao)

        val result = router.matchSkills("commit and refactor the code")

        assertTrue("Should contain git_commit skill", result.contains("# Skill: git_commit"))
        assertTrue("Should contain code_refactor skill", result.contains("# Skill: code_refactor"))
    }

    @Test
    fun `matchSkills ignores disabled skills`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(
                id = 1,
                name = "disabled_skill",
                triggerPatterns = "commit",
                promptInjection = "Should not appear",
                isEnabled = false
            )
        ))
        val router = SkillRouter(dao)

        val result = router.matchSkills("commit my changes")

        assertEquals("Disabled skill should not match", "", result)
    }

    @Test
    fun `matchSkills is case insensitive`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(
                id = 1,
                name = "git_commit",
                triggerPatterns = "commit",
                promptInjection = "Commit advice",
                isEnabled = true
            )
        ))
        val router = SkillRouter(dao)

        val result = router.matchSkills("COMMIT my changes NOW")

        assertTrue("Case-insensitive match", result.contains("# Skill: git_commit"))
    }

    // ─── seedBuiltInSkills ───────────────────────────────────────────────────

    @Test
    fun `seedBuiltInSkills only runs when count is 0`() = runBlocking {
        val dao = FakeSkillDao(listOf(
            SkillEntity(id = 1, name = "existing", triggerPatterns = "x", isEnabled = true)
        ))
        val router = SkillRouter(dao)
        val countBefore = dao.getCount()

        router.seedBuiltInSkills()

        assertEquals("Should not seed when skills already exist", countBefore, dao.getCount())
    }

    @Test
    fun `seedBuiltInSkills inserts skills when empty`() = runBlocking {
        val dao = FakeSkillDao(emptyList())
        val router = SkillRouter(dao)

        router.seedBuiltInSkills()

        assertTrue("Should have seeded skills", dao.getCount() > 0)
    }
}
