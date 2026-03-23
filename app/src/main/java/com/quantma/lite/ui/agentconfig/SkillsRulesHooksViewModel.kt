package com.quantma.lite.ui.agentconfig

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantma.lite.data.local.db.HookDao
import com.quantma.lite.data.local.db.RuleDao
import com.quantma.lite.data.local.db.SkillDao
import com.quantma.lite.data.local.db.entity.HookEntity
import com.quantma.lite.data.local.db.entity.RuleEntity
import com.quantma.lite.data.local.db.entity.SkillEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SkillsRulesHooksViewModel @Inject constructor(
    private val skillDao: SkillDao,
    private val ruleDao: RuleDao,
    private val hookDao: HookDao
) : ViewModel() {

    val skills: StateFlow<List<SkillEntity>> = skillDao
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rules: StateFlow<List<RuleEntity>> = ruleDao
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hooks: StateFlow<List<HookEntity>> = hookDao
        .observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Skills ----

    fun toggleSkill(id: Long, enabled: Boolean) {
        viewModelScope.launch { skillDao.setEnabled(id, enabled) }
    }

    fun deleteSkill(skill: SkillEntity) {
        viewModelScope.launch { skillDao.delete(skill) }
    }

    fun addSkill(name: String, description: String, triggerPatterns: String, promptInjection: String) {
        viewModelScope.launch {
            skillDao.insert(
                SkillEntity(
                    name = name,
                    description = description,
                    triggerPatterns = triggerPatterns,
                    promptInjection = promptInjection,
                    isEnabled = true,
                    isBuiltIn = false
                )
            )
        }
    }

    // ---- Rules ----

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { ruleDao.setEnabled(id, enabled) }
    }

    fun deleteRule(rule: RuleEntity) {
        viewModelScope.launch { ruleDao.delete(rule) }
    }

    fun addRule(category: String, instruction: String) {
        viewModelScope.launch {
            ruleDao.insert(
                RuleEntity(
                    category = category,
                    instruction = instruction,
                    isEnabled = true,
                    isBuiltIn = false
                )
            )
        }
    }

    // ---- Hooks ----

    fun toggleHook(id: Long, enabled: Boolean) {
        viewModelScope.launch { hookDao.setEnabled(id, enabled) }
    }

    fun deleteHook(hook: HookEntity) {
        viewModelScope.launch { hookDao.delete(hook) }
    }

    fun addHook(trigger: String, action: String) {
        viewModelScope.launch {
            hookDao.insert(
                HookEntity(
                    trigger = trigger,
                    action = action,
                    isEnabled = true,
                    isBuiltIn = false
                )
            )
        }
    }
}
