package com.quantma.lite.ui.agentconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import com.quantma.lite.data.local.db.entity.HookEntity
import com.quantma.lite.data.local.db.entity.RuleEntity
import com.quantma.lite.data.local.db.entity.SkillEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsRulesHooksScreen(
    onBack: () -> Unit,
    viewModel: SkillsRulesHooksViewModel = hiltViewModel()
) {
    val skills by viewModel.skills.collectAsState()
    val rules by viewModel.rules.collectAsState()
    val hooks by viewModel.hooks.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    val tabs = listOf(
        stringResource(R.string.tab_skills),
        stringResource(R.string.tab_rules),
        stringResource(R.string.tab_hooks)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.srh_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        icon = {
                            Icon(
                                imageVector = when (index) {
                                    0 -> Icons.Default.AutoFixHigh
                                    1 -> Icons.Default.Gavel
                                    else -> Icons.Default.Webhook
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> SkillsTab(skills, viewModel)
                1 -> RulesTab(rules, viewModel)
                2 -> HooksTab(hooks, viewModel)
            }
        }
    }

    if (showAddDialog) {
        when (selectedTab) {
            0 -> AddSkillDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { name, desc, triggers, prompt ->
                    viewModel.addSkill(name, desc, triggers, prompt)
                    showAddDialog = false
                }
            )
            1 -> AddRuleDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { category, instruction ->
                    viewModel.addRule(category, instruction)
                    showAddDialog = false
                }
            )
            2 -> AddHookDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { trigger, action ->
                    viewModel.addHook(trigger, action)
                    showAddDialog = false
                }
            )
        }
    }
}

// ======== Skills Tab ========

@Composable
private fun SkillsTab(skills: List<SkillEntity>, viewModel: SkillsRulesHooksViewModel) {
    var editingSkill by remember { mutableStateOf<SkillEntity?>(null) }

    if (skills.isEmpty()) {
        EmptyState(stringResource(R.string.skills_empty))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(skills, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    onToggle = { viewModel.toggleSkill(skill.id, it) },
                    onDelete = if (!skill.isBuiltIn) {{ viewModel.deleteSkill(skill) }} else null,
                    onEdit = if (!skill.isBuiltIn) {{ editingSkill = skill }} else null
                )
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    editingSkill?.let { skill ->
        EditSkillDialog(
            skill = skill,
            onDismiss = { editingSkill = null },
            onConfirm = { name, desc, triggers, prompt ->
                viewModel.updateSkill(skill, name, desc, triggers, prompt)
                editingSkill = null
            }
        )
    }
}

@Composable
private fun SkillCard(
    skill: SkillEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (skill.isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = skill.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (skill.isBuiltIn) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = stringResource(R.string.built_in),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (skill.description.isNotEmpty()) {
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = skill.triggerPatterns,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = skill.isEnabled,
                    onCheckedChange = onToggle
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_skill),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ======== Rules Tab ========

@Composable
private fun RulesTab(rules: List<RuleEntity>, viewModel: SkillsRulesHooksViewModel) {
    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }

    if (rules.isEmpty()) {
        EmptyState(stringResource(R.string.rules_empty))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { viewModel.toggleRule(rule.id, it) },
                    onDelete = if (!rule.isBuiltIn) {{ viewModel.deleteRule(rule) }} else null,
                    onEdit = if (!rule.isBuiltIn) {{ editingRule = rule }} else null
                )
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    editingRule?.let { rule ->
        EditRuleDialog(
            rule = rule,
            onDismiss = { editingRule = null },
            onConfirm = { category, instruction ->
                viewModel.updateRule(rule, category, instruction)
                editingRule = null
            }
        )
    }
}

@Composable
private fun RuleCard(
    rule: RuleEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = formatCategoryDisplay(rule.category),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (rule.isBuiltIn) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = stringResource(R.string.built_in),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = rule.instruction,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = onToggle
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_rule),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ======== Hooks Tab ========

@Composable
private fun HooksTab(hooks: List<HookEntity>, viewModel: SkillsRulesHooksViewModel) {
    var editingHook by remember { mutableStateOf<HookEntity?>(null) }

    if (hooks.isEmpty()) {
        EmptyState(stringResource(R.string.hooks_empty))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hooks, key = { it.id }) { hook ->
                HookCard(
                    hook = hook,
                    onToggle = { viewModel.toggleHook(hook.id, it) },
                    onDelete = if (!hook.isBuiltIn) {{ viewModel.deleteHook(hook) }} else null,
                    onEdit = if (!hook.isBuiltIn) {{ editingHook = hook }} else null
                )
            }
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }

    editingHook?.let { hook ->
        EditHookDialog(
            hook = hook,
            onDismiss = { editingHook = null },
            onConfirm = { trigger, action ->
                viewModel.updateHook(hook, trigger, action)
                editingHook = null
            }
        )
    }
}

@Composable
private fun HookCard(
    hook: HookEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)?,
    onEdit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hook.isEnabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = formatTriggerDisplay(hook.trigger),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (hook.isBuiltIn) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiary
                            ) {
                                Text(
                                    text = stringResource(R.string.built_in),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = hook.action,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = hook.isEnabled,
                    onCheckedChange = onToggle
                )
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_hook),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ======== Empty State ========

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ======== Add Dialogs ========

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, triggers: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var triggers by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_skill)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.skill_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.skill_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = triggers,
                    onValueChange = { triggers = it },
                    label = { Text(stringResource(R.string.skill_triggers)) },
                    placeholder = { Text(stringResource(R.string.skill_triggers_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.skill_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), triggers.trim(), prompt.trim()) },
                enabled = name.isNotBlank() && triggers.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRuleDialog(
    onDismiss: () -> Unit,
    onConfirm: (category: String, instruction: String) -> Unit
) {
    val categories = listOf("CODE_STYLE", "ARCHITECTURE", "SECURITY", "COMMENTS")
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var instruction by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = formatCategoryDisplay(selectedCategory),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rule_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(formatCategoryDisplay(cat)) },
                                onClick = {
                                    selectedCategory = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.rule_instruction)) },
                    placeholder = { Text(stringResource(R.string.rule_instruction_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCategory, instruction.trim()) },
                enabled = instruction.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHookDialog(
    onDismiss: () -> Unit,
    onConfirm: (trigger: String, action: String) -> Unit
) {
    val triggers = listOf("PRE_COMMIT", "POST_COMMIT", "ON_FILE_CHANGE", "ON_ERROR")
    var selectedTrigger by remember { mutableStateOf(triggers[0]) }
    var action by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_hook)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = formatTriggerDisplay(selectedTrigger),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.hook_trigger)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        triggers.forEach { trig ->
                            DropdownMenuItem(
                                text = { Text(formatTriggerDisplay(trig)) },
                                onClick = {
                                    selectedTrigger = trig
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text(stringResource(R.string.hook_action)) },
                    placeholder = { Text(stringResource(R.string.hook_action_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTrigger, action.trim()) },
                enabled = action.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ======== Edit Dialogs ========

@Composable
private fun EditSkillDialog(
    skill: SkillEntity,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, triggers: String, prompt: String) -> Unit
) {
    var name by remember { mutableStateOf(skill.name) }
    var description by remember { mutableStateOf(skill.description) }
    var triggers by remember { mutableStateOf(skill.triggerPatterns) }
    var prompt by remember { mutableStateOf(skill.promptInjection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_skill)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.skill_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.skill_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = triggers,
                    onValueChange = { triggers = it },
                    label = { Text(stringResource(R.string.skill_triggers)) },
                    placeholder = { Text(stringResource(R.string.skill_triggers_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.skill_prompt)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), triggers.trim(), prompt.trim()) },
                enabled = name.isNotBlank() && triggers.isNotBlank() && prompt.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRuleDialog(
    rule: RuleEntity,
    onDismiss: () -> Unit,
    onConfirm: (category: String, instruction: String) -> Unit
) {
    val categories = listOf("CODE_STYLE", "ARCHITECTURE", "SECURITY", "COMMENTS")
    var selectedCategory by remember { mutableStateOf(rule.category) }
    var instruction by remember { mutableStateOf(rule.instruction) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = formatCategoryDisplay(selectedCategory),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.rule_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(formatCategoryDisplay(cat)) },
                                onClick = {
                                    selectedCategory = cat
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.rule_instruction)) },
                    placeholder = { Text(stringResource(R.string.rule_instruction_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCategory, instruction.trim()) },
                enabled = instruction.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHookDialog(
    hook: HookEntity,
    onDismiss: () -> Unit,
    onConfirm: (trigger: String, action: String) -> Unit
) {
    val triggerOptions = listOf("PRE_COMMIT", "POST_COMMIT", "ON_FILE_CHANGE", "ON_ERROR")
    var selectedTrigger by remember { mutableStateOf(hook.trigger) }
    var action by remember { mutableStateOf(hook.action) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_hook)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = formatTriggerDisplay(selectedTrigger),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.hook_trigger)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        triggerOptions.forEach { trig ->
                            DropdownMenuItem(
                                text = { Text(formatTriggerDisplay(trig)) },
                                onClick = {
                                    selectedTrigger = trig
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = action,
                    onValueChange = { action = it },
                    label = { Text(stringResource(R.string.hook_action)) },
                    placeholder = { Text(stringResource(R.string.hook_action_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedTrigger, action.trim()) },
                enabled = action.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ======== Helpers ========

private fun formatCategoryDisplay(category: String): String = when (category) {
    "CODE_STYLE" -> "Code Style"
    "ARCHITECTURE" -> "Architecture"
    "SECURITY" -> "Security"
    "COMMENTS" -> "Comments"
    else -> category
}

private fun formatTriggerDisplay(trigger: String): String = when (trigger) {
    "PRE_COMMIT" -> "Before Commit"
    "POST_COMMIT" -> "After Commit"
    "ON_FILE_CHANGE" -> "On File Change"
    "ON_ERROR" -> "On Error"
    else -> trigger
}
