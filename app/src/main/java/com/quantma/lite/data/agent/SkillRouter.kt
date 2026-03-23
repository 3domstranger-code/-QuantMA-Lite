package com.quantma.lite.data.agent

import timber.log.Timber
import com.quantma.lite.data.local.db.SkillDao
import com.quantma.lite.data.local.db.entity.SkillEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Matches user input against enabled skill trigger patterns.
 * When a match is found, injects the skill's prompt instructions
 * into the system prompt for that agent round.
 */
@Singleton
class SkillRouter @Inject constructor(
    private val skillDao: SkillDao
) {
    /**
     * Find matching skills for the given user message.
     * Returns combined prompt injection text from all matched skills.
     * Returns empty string if no skills match.
     */
    suspend fun matchSkills(userMessage: String): String {
        val enabledSkills = skillDao.getEnabled()
        if (enabledSkills.isEmpty()) return ""

        val matched = mutableListOf<SkillEntity>()
        val lowerMessage = userMessage.lowercase()

        for (skill in enabledSkills) {
            val patterns = skill.triggerPatterns
                .split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }

            val isMatch = patterns.any { pattern ->
                try {
                    // Try as regex first
                    Regex(pattern).containsMatchIn(lowerMessage)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse skill trigger")
                    // Fall back to simple keyword match
                    lowerMessage.contains(pattern)
                }
            }

            if (isMatch) {
                matched.add(skill)
                Timber.d("Skill matched: ${skill.name}")
            }
        }

        if (matched.isEmpty()) return ""

        return matched.joinToString("\n\n") { skill ->
            "# Skill: ${skill.name}\n${skill.promptInjection}"
        }
    }

    /**
     * Seed built-in skills if the database is empty.
     * Called once on app startup.
     */
    suspend fun seedBuiltInSkills() {
        if (skillDao.getCount() > 0) return

        val builtIns = listOf(
            SkillEntity(
                name = "git_commit",
                description = "Best practices for writing git commit messages",
                triggerPatterns = "commit,git commit,коммит",
                promptInjection = """When making a git commit:
- Write a clear, concise commit message
- Use imperative mood ("Add feature" not "Added feature")
- First line should be under 50 characters
- If needed, add a blank line then detailed description
- Reference issue numbers if applicable""",
                isEnabled = true,
                isBuiltIn = true
            ),
            SkillEntity(
                name = "code_refactor",
                description = "Guidelines for code refactoring",
                triggerPatterns = "refactor,рефакторинг,clean up,restructure",
                promptInjection = """When refactoring code:
- Preserve existing behavior (no functional changes)
- Extract repeated code into functions
- Simplify complex conditionals
- Improve naming for clarity
- Keep functions small and focused
- Add comments explaining non-obvious logic
- Test after each change""",
                isEnabled = true,
                isBuiltIn = true
            ),
            SkillEntity(
                name = "write_tests",
                description = "Guidelines for writing tests",
                triggerPatterns = "test,тест,unit test,write test",
                promptInjection = """When writing tests:
- Follow AAA pattern: Arrange, Act, Assert
- Test one behavior per test function
- Use descriptive test names that explain the scenario
- Cover edge cases and error conditions
- Mock external dependencies
- Keep tests independent of each other""",
                isEnabled = true,
                isBuiltIn = true
            ),
            SkillEntity(
                name = "explain_code",
                description = "Guidelines for explaining code",
                triggerPatterns = "explain,объясни,what does,how does,как работает",
                promptInjection = """When explaining code:
- Start with a high-level overview
- Break down complex logic step by step
- Explain the "why" not just the "what"
- Use simple language, avoid jargon
- Point out design patterns if applicable
- Mention potential issues or improvements""",
                isEnabled = true,
                isBuiltIn = true
            )
        )

        for (skill in builtIns) {
            skillDao.insert(skill)
        }
        Timber.i("Seeded ${builtIns.size} built-in skills")
    }
}
