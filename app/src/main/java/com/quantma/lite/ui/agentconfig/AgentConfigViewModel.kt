package com.quantma.lite.ui.agentconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.local.db.AgentConfigDao
import com.quantma.lite.data.local.db.entity.AgentConfigEntity
import com.quantma.lite.domain.model.AgentRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentConfigViewModel @Inject constructor(
    private val agentConfigDao: AgentConfigDao
) : ViewModel() {

    val configs: StateFlow<List<AgentConfigEntity>> = agentConfigDao
        .getAllConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setDefault(id: Long) {
        viewModelScope.launch {
            agentConfigDao.clearAllDefaults()
            agentConfigDao.setDefault(id)
        }
    }

    fun delete(config: AgentConfigEntity) {
        viewModelScope.launch {
            agentConfigDao.delete(config)
        }
    }

    /** Save a custom config (insert or replace by id). */
    fun save(
        id: Long = 0,
        name: String,
        systemPrompt: String,
        role: AgentRole = AgentRole.ASSISTANT,
        customInstructions: String = "",
        restrictions: String = ""
    ) {
        viewModelScope.launch {
            val isFirst = agentConfigDao.getCount() == 0
            val entity = AgentConfigEntity(
                id = id,
                name = name,
                systemPrompt = systemPrompt,
                role = role.name,
                customInstructions = customInstructions,
                restrictions = restrictions,
                isDefault = isFirst  // auto-activate if first config
            )
            agentConfigDao.insert(entity)
        }
    }

    /** Insert a preset config. Auto-activates if it's the first one. */
    fun applyPreset(role: AgentRole) {
        viewModelScope.launch {
            val count = agentConfigDao.getCount()
            val entity = AgentConfigEntity(
                name = role.displayName(),
                systemPrompt = role.defaultSystemPrompt(),
                role = role.name,
                isDefault = count == 0
            )
            agentConfigDao.insert(entity)
        }
    }
}

// ---- Extension helpers ----

fun AgentRole.displayName(): String = when (this) {
    AgentRole.ASSISTANT  -> "Code Assistant"
    AgentRole.REVIEWER   -> "Code Reviewer"
    AgentRole.TESTER     -> "Test Writer"
    AgentRole.DOCUMENTER -> "Documenter"
}

fun AgentRole.defaultSystemPrompt(): String = when (this) {
    AgentRole.ASSISTANT ->
        "You are a helpful coding assistant. Provide clear, concise code and explanations. " +
        "When asked to write code, use appropriate markdown code blocks with language tags."
    AgentRole.REVIEWER ->
        "You are a code reviewer. Analyze code for bugs, performance issues, and best practices. " +
        "Provide actionable feedback with specific line references and improvement suggestions."
    AgentRole.TESTER ->
        "You are a test engineer. Write comprehensive unit tests and integration tests. " +
        "Cover edge cases, boundary conditions, and use the appropriate testing framework for the language."
    AgentRole.DOCUMENTER ->
        "You are a technical writer. Write clear, comprehensive documentation for code. " +
        "Include parameter descriptions, return values, usage examples, and explain the reasoning behind design decisions."
}
