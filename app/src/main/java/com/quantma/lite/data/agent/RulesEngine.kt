package com.quantma.lite.data.agent

import timber.log.Timber
import com.quantma.lite.data.local.db.RuleDao
import com.quantma.lite.data.local.db.entity.RuleEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads all enabled rules and formats them as code-quality instructions
 * to be appended to the agent's system prompt.
 *
 * Rules are persistent — they apply to every agent interaction unlike
 * skills which are triggered by user input.
 */
@Singleton
class RulesEngine @Inject constructor(
    private val ruleDao: RuleDao
) {
    /**
     * Build a rules block to append to the system prompt.
     * Returns empty string if no rules are enabled.
     */
    suspend fun buildRulesBlock(): String {
        val enabledRules = ruleDao.getEnabled()
        if (enabledRules.isEmpty()) return ""

        val grouped = enabledRules.groupBy { it.category }

        val sb = StringBuilder()
        sb.appendLine("\n# Code Quality Rules")
        sb.appendLine("Always follow these rules when writing or modifying code:")

        for ((category, rules) in grouped) {
            sb.appendLine("\n## ${formatCategory(category)}")
            for (rule in rules) {
                sb.appendLine("- ${rule.instruction}")
            }
        }

        Timber.d("Built rules block with ${enabledRules.size} rules")
        return sb.toString()
    }

    private fun formatCategory(category: String): String {
        return when (category) {
            "CODE_STYLE" -> "Code Style"
            "ARCHITECTURE" -> "Architecture"
            "SECURITY" -> "Security"
            "COMMENTS" -> "Comments & Documentation"
            else -> category
        }
    }

    /**
     * Seed default rules if the database is empty.
     * Called once on app startup.
     */
    suspend fun seedDefaultRules() {
        if (ruleDao.getCount() > 0) return

        val defaults = listOf(
            RuleEntity(
                category = "CODE_STYLE",
                instruction = "Use descriptive, meaningful variable and function names. Avoid single-letter names except for loop counters.",
                isEnabled = true,
                isBuiltIn = true
            ),
            RuleEntity(
                category = "CODE_STYLE",
                instruction = "Keep functions short and focused. Each function should do one thing well.",
                isEnabled = true,
                isBuiltIn = true
            ),
            RuleEntity(
                category = "ARCHITECTURE",
                instruction = "Follow the Single Responsibility Principle: each class/module should have one reason to change.",
                isEnabled = true,
                isBuiltIn = true
            ),
            RuleEntity(
                category = "ARCHITECTURE",
                instruction = "Prefer composition over inheritance. Use interfaces for abstraction.",
                isEnabled = true,
                isBuiltIn = true
            ),
            RuleEntity(
                category = "SECURITY",
                instruction = "Always include proper error handling. Never ignore exceptions silently.",
                isEnabled = true,
                isBuiltIn = true
            ),
            RuleEntity(
                category = "SECURITY",
                instruction = "Validate all input data. Never trust user input without sanitization.",
                isEnabled = true,
                isBuiltIn = true
            ),
            RuleEntity(
                category = "COMMENTS",
                instruction = "Add comments for non-obvious logic. Code should be self-documenting where possible.",
                isEnabled = true,
                isBuiltIn = true
            )
        )

        for (rule in defaults) {
            ruleDao.insert(rule)
        }
        Timber.i("Seeded ${defaults.size} default rules")
    }
}
