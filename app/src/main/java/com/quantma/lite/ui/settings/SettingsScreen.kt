package com.quantma.lite.ui.settings

import android.provider.DocumentsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.BuildConfig
import com.quantma.lite.R
import com.quantma.lite.data.download.DownloadState
import com.quantma.lite.ui.theme.CustomThemeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToAgentConfig: () -> Unit = {},
    onNavigateToSkillsRulesHooks: () -> Unit = {},
    onNavigateToGitCredentials: () -> Unit = {},
    onNavigateToModelCatalog: () -> Unit = {},
    onNavigateToLicenses: () -> Unit = {}
) {
    val modelPath by viewModel.modelPath.collectAsState()
    val nThreads by viewModel.nThreads.collectAsState()
    val contextSize by viewModel.contextSize.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val modelsDir by viewModel.modelsDir.collectAsState()
    val agentWorkingDir by viewModel.agentWorkingDir.collectAsState()
    val agentMaxRounds by viewModel.agentMaxRounds.collectAsState()
    val language by viewModel.language.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val codeBlockFontSize by viewModel.codeBlockFontSize.collectAsState()
    val compressionEnabled by viewModel.compressionEnabled.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val topP by viewModel.topP.collectAsState()
    val repeatPenalty by viewModel.repeatPenalty.collectAsState()
    val useGpu by viewModel.useGpu.collectAsState()
    val gpuLayers by viewModel.gpuLayers.collectAsState()
    // Phase 10 (v1.9.0)
    val downloadState by viewModel.downloadState.collectAsState()
    val loraEnabled by viewModel.loraEnabled.collectAsState()
    val loraPath by viewModel.loraPath.collectAsState()
    val loraScale by viewModel.loraScale.collectAsState()

    val cloneState by viewModel.cloneState.collectAsState()

    // Custom Theme (Phase 12)
    val customBgUri by viewModel.customBgUri.collectAsState()
    val customBgOpacity by viewModel.customBgOpacity.collectAsState()
    val customUserBubble by viewModel.customUserBubble.collectAsState()
    val customAssistantBubble by viewModel.customAssistantBubble.collectAsState()
    val customAccent by viewModel.customAccent.collectAsState()
    val customPanel by viewModel.customPanel.collectAsState()

    // Extended colors (v2.11.0)
    val customThermalOk by viewModel.customThermalOk.collectAsState()
    val customThermalWarn by viewModel.customThermalWarn.collectAsState()
    val customThermalHot by viewModel.customThermalHot.collectAsState()
    val customCpuHigh by viewModel.customCpuHigh.collectAsState()
    val customGpuBar by viewModel.customGpuBar.collectAsState()
    val customBackendBadge by viewModel.customBackendBadge.collectAsState()

    val advancedMode by viewModel.advancedMode.collectAsState()

    // Auto/manual mode flags
    val autoThreads by viewModel.autoThreads.collectAsState()
    val autoContextSize by viewModel.autoContextSize.collectAsState()
    val autoBatchSize by viewModel.autoBatchSize.collectAsState()
    val autoGpuLayers by viewModel.autoGpuLayers.collectAsState()
    val autoFlashAttention by viewModel.autoFlashAttention.collectAsState()
    val autoMlock by viewModel.autoMlock.collectAsState()
    val autoTemperature by viewModel.autoTemperature.collectAsState()
    val autoTopP by viewModel.autoTopP.collectAsState()
    val autoTopK by viewModel.autoTopK.collectAsState()
    val autoMinP by viewModel.autoMinP.collectAsState()
    val autoRepeatPenalty by viewModel.autoRepeatPenalty.collectAsState()
    val autoPenaltyLastN by viewModel.autoPenaltyLastN.collectAsState()

    // New inference params
    val batchSize by viewModel.batchSize.collectAsState()
    val flashAttention by viewModel.flashAttention.collectAsState()
    val mlock by viewModel.mlock.collectAsState()

    // Extended sampling params
    val topK by viewModel.topK.collectAsState()
    val minP by viewModel.minP.collectAsState()
    val penaltyLastN by viewModel.penaltyLastN.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current

    val bgPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // Persist read permission across reboots
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.setCustomBgUri(it.toString())
        }
    }

    var showDownloadDialog by remember { mutableStateOf(false) }
    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneUrl by remember { mutableStateOf("") }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val docId = DocumentsContract.getTreeDocumentId(it)
            val path = when {
                docId.startsWith("primary:") ->
                    "/storage/emulated/0/${docId.removePrefix("primary:")}"
                else -> null
            }
            path?.let { p -> viewModel.setAgentWorkingDir(p) }
        }
    }

    if (showDownloadDialog) {
        DownloadModelDialog(
            downloadState = downloadState,
            onDownload = { url ->
                viewModel.startDownload(url)
            },
            onCancel = {
                viewModel.cancelDownload()
            },
            onDismiss = {
                if (downloadState !is DownloadState.Downloading) {
                    viewModel.resetDownloadState()
                    showDownloadDialog = false
                }
            }
        )
    }

    if (showCloneDialog) {
        CloneRepoDialog(
            cloneState = cloneState,
            cloneUrl = cloneUrl,
            onUrlChange = { cloneUrl = it },
            onClone = { viewModel.cloneAndSetWorkingDir(cloneUrl) },
            onDismiss = {
                showCloneDialog = false
                viewModel.resetCloneState()
                cloneUrl = ""
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
            // Settings Mode toggle (v2.7.0)
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_mode_label),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (advancedMode) stringResource(R.string.settings_mode_advanced)
                            else stringResource(R.string.settings_mode_simple),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = advancedMode,
                        onCheckedChange = { viewModel.setAdvancedMode(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Model section
            SectionHeader(stringResource(R.string.section_model), Icons.Default.Description)

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.models_directory),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = modelsDir.ifEmpty { stringResource(R.string.not_available) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { viewModel.refreshModels() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Download Model button (Phase 10 v1.9.0)
                    OutlinedButton(
                        onClick = { showDownloadDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.download_model))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Model Catalog button (Phase 9 v1.9.1)
                    OutlinedButton(
                        onClick = onNavigateToModelCatalog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(stringResource(R.string.model_catalog))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (availableModels.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_models_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.available_models, availableModels.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        availableModels.forEach { modelFile ->
                            val isSelected = modelPath == modelFile.absolutePath
                            val fileSizeMb = modelFile.length() / (1024.0 * 1024.0)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clickable {
                                        if (isSelected) {
                                            viewModel.requestModelReload()
                                        } else {
                                            viewModel.setModelPath(modelFile.absolutePath)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isSelected) Icons.Default.CheckCircle
                                        else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = modelFile.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.size_mb, fileSizeMb),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Inference section
            SectionHeader(stringResource(R.string.section_inference), Icons.Default.Settings)

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Threads — auto/manual
                    AutoManualSlider(
                        label = stringResource(R.string.threads),
                        value = nThreads.toFloat(),
                        isAuto = autoThreads,
                        onAutoChange = { viewModel.setAutoThreads(it) },
                        onValueChange = { viewModel.setNThreads(it.toInt()) },
                        valueRange = 1f..8f,
                        steps = 6,
                        valueLabel = "$nThreads",
                        hint = stringResource(R.string.hint_threads),
                        autoHint = stringResource(R.string.hint_auto_threads)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Context size — auto/manual
                    AutoManualSlider(
                        label = stringResource(R.string.context_size),
                        value = contextSize.toFloat(),
                        isAuto = autoContextSize,
                        onAutoChange = { viewModel.setAutoContextSize(it) },
                        onValueChange = { viewModel.setContextSize(it.toInt()) },
                        valueRange = 512f..8192f,
                        steps = 14,
                        valueLabel = "$contextSize",
                        hint = stringResource(R.string.hint_context_size),
                        autoHint = stringResource(R.string.hint_auto_context_size)
                    )

                    // Advanced inference params — only in advanced mode
                    AnimatedVisibility(visible = advancedMode) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Batch size — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.batch_size),
                                value = batchSize.toFloat(),
                                isAuto = autoBatchSize,
                                onAutoChange = { viewModel.setAutoBatchSize(it) },
                                onValueChange = { viewModel.setBatchSize(it.toInt()) },
                                valueRange = 128f..2048f,
                                steps = 14,
                                valueLabel = "$batchSize",
                                hint = stringResource(R.string.hint_batch_size),
                                autoHint = stringResource(R.string.hint_auto_batch_size)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Flash Attention — auto/manual toggle
                            AutoManualToggle(
                                label = stringResource(R.string.flash_attention),
                                checked = flashAttention,
                                isAuto = autoFlashAttention,
                                onAutoChange = { viewModel.setAutoFlashAttention(it) },
                                onCheckedChange = { viewModel.setFlashAttention(it) },
                                hint = stringResource(R.string.hint_flash_attention),
                                autoHint = stringResource(R.string.hint_auto_flash_attention)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Mlock — auto/manual toggle
                            AutoManualToggle(
                                label = stringResource(R.string.mlock),
                                checked = mlock,
                                isAuto = autoMlock,
                                onAutoChange = { viewModel.setAutoMlock(it) },
                                onCheckedChange = { viewModel.setMlock(it) },
                                hint = stringResource(R.string.hint_mlock),
                                autoHint = stringResource(R.string.hint_auto_mlock)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language section
            SectionHeader(stringResource(R.string.section_language), Icons.Default.Language)

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val langOptions = listOf(
                        "system" to stringResource(R.string.language_system),
                        "en" to stringResource(R.string.language_en),
                        "ru" to stringResource(R.string.language_ru)
                    )

                    langOptions.forEach { (code, label) ->
                        val isSelected = language == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(code)
                                    val locales = if (code == "system") {
                                        LocaleListCompat.getEmptyLocaleList()
                                    } else {
                                        LocaleListCompat.forLanguageTags(code)
                                    }
                                    AppCompatDelegate.setApplicationLocales(locales)
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSelected) Icons.Default.CheckCircle
                                else Icons.Default.Language,
                                contentDescription = null,
                                tint = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Theme section
            SectionHeader("Тема оформления", Icons.Default.Settings)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val themeOptions = listOf(
                        "SYSTEM" to "Системная",
                        "LIGHT"  to "Светлая",
                        "DARK"   to "Тёмная",
                        "AMOLED" to "AMOLED (чистый чёрный)"
                    )
                    themeOptions.forEach { (code, label) ->
                        val isSelected = themeMode == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeMode(code) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSelected) Icons.Default.CheckCircle else Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Code font size section
            SectionHeader("Размер шрифта кода", Icons.Default.Code)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Размер: ${codeBlockFontSize.toInt()} sp",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { viewModel.setCodeBlockFontSize(13f) }) {
                            Text("Сброс")
                        }
                    }
                    Slider(
                        value = codeBlockFontSize,
                        onValueChange = { viewModel.setCodeBlockFontSize(it) },
                        valueRange = 10f..20f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Advanced sections
            AnimatedVisibility(visible = advancedMode) {
                Column {
                    // Sampling section (Phase 1 v1.0.0)
                    SectionHeader(stringResource(R.string.section_sampling), Icons.Default.Tune)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Temperature — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.sampling_temperature),
                                value = temperature,
                                isAuto = autoTemperature,
                                onAutoChange = { viewModel.setAutoTemperature(it) },
                                onValueChange = { viewModel.setTemperature(it) },
                                valueRange = 0f..2f,
                                steps = 19,
                                valueLabel = "%.2f".format(temperature),
                                hint = stringResource(R.string.sampling_temperature_hint),
                                autoHint = stringResource(R.string.hint_auto_sampling)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Top-P — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.sampling_top_p),
                                value = topP,
                                isAuto = autoTopP,
                                onAutoChange = { viewModel.setAutoTopP(it) },
                                onValueChange = { viewModel.setTopP(it) },
                                valueRange = 0f..1f,
                                steps = 9,
                                valueLabel = "%.2f".format(topP),
                                hint = stringResource(R.string.hint_top_p),
                                autoHint = stringResource(R.string.hint_auto_sampling)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Top-K — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.sampling_top_k),
                                value = topK.toFloat(),
                                isAuto = autoTopK,
                                onAutoChange = { viewModel.setAutoTopK(it) },
                                onValueChange = { viewModel.setTopK(it.toInt()) },
                                valueRange = 0f..100f,
                                steps = 9,
                                valueLabel = "$topK",
                                hint = stringResource(R.string.hint_top_k),
                                autoHint = stringResource(R.string.hint_auto_sampling)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Min-P — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.sampling_min_p),
                                value = minP,
                                isAuto = autoMinP,
                                onAutoChange = { viewModel.setAutoMinP(it) },
                                onValueChange = { viewModel.setMinP(it) },
                                valueRange = 0f..0.5f,
                                steps = 9,
                                valueLabel = "%.3f".format(minP),
                                hint = stringResource(R.string.hint_min_p),
                                autoHint = stringResource(R.string.hint_auto_sampling)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Repeat Penalty — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.sampling_repeat_penalty),
                                value = repeatPenalty,
                                isAuto = autoRepeatPenalty,
                                onAutoChange = { viewModel.setAutoRepeatPenalty(it) },
                                onValueChange = { viewModel.setRepeatPenalty(it) },
                                valueRange = 1f..2f,
                                steps = 9,
                                valueLabel = "%.2f".format(repeatPenalty),
                                hint = stringResource(R.string.hint_repeat_penalty),
                                autoHint = stringResource(R.string.hint_auto_sampling)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Penalty Last N — auto/manual
                            AutoManualSlider(
                                label = stringResource(R.string.sampling_penalty_last_n),
                                value = penaltyLastN.toFloat(),
                                isAuto = autoPenaltyLastN,
                                onAutoChange = { viewModel.setAutoPenaltyLastN(it) },
                                onValueChange = { viewModel.setPenaltyLastN(it.toInt()) },
                                valueRange = 0f..256f,
                                steps = 15,
                                valueLabel = "$penaltyLastN",
                                hint = stringResource(R.string.hint_penalty_last_n),
                                autoHint = stringResource(R.string.hint_auto_sampling)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dialog Compression section
                    SectionHeader(stringResource(R.string.section_compression), Icons.Default.Memory)

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.compression_toggle),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = compressionEnabled,
                                    onCheckedChange = { viewModel.setCompressionEnabled(it) }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.compression_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Agent Mode section
                    SectionHeader(stringResource(R.string.section_agent_mode), Icons.Default.SmartToy)

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.working_directory),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = agentWorkingDir,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(stringResource(R.string.pick_folder))
                                }
                                OutlinedButton(
                                    onClick = { showCloneDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text(stringResource(R.string.clone_from_git))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.working_dir_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.max_rounds),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$agentMaxRounds",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Slider(
                                value = agentMaxRounds.toFloat(),
                                onValueChange = { viewModel.setAgentMaxRounds(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8
                            )
                            Text(
                                text = stringResource(R.string.hint_max_rounds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Agent Configuration section (Phase 2 v1.1.0)
                    SectionHeader(stringResource(R.string.section_agent_config), Icons.Default.Settings)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToAgentConfig() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.agent_config_title),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = stringResource(R.string.agent_config_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Skills, Rules & Hooks section (Phase 5 v1.4.0)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSkillsRulesHooks() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.srh_title),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = stringResource(R.string.srh_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // GPU Acceleration section (Phase 7 v1.6.0)
                    SectionHeader(stringResource(R.string.section_gpu), Icons.Default.Memory)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.gpu_use_vulkan),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = useGpu,
                                    onCheckedChange = { viewModel.setUseGpu(it) }
                                )
                            }
                            Text(
                                text = stringResource(R.string.gpu_vulkan_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (useGpu) {
                                Spacer(modifier = Modifier.height(16.dp))
                                AutoManualSlider(
                                    label = stringResource(R.string.gpu_layers),
                                    value = gpuLayers.toFloat(),
                                    isAuto = autoGpuLayers,
                                    onAutoChange = { viewModel.setAutoGpuLayers(it) },
                                    onValueChange = { viewModel.setGpuLayers(it.toInt()) },
                                    valueRange = 0f..99f,
                                    steps = 32,
                                    valueLabel = if (gpuLayers == 0 || autoGpuLayers) stringResource(R.string.gpu_layers_auto) else "$gpuLayers",
                                    hint = stringResource(R.string.gpu_layers_hint),
                                    autoHint = stringResource(R.string.hint_auto_gpu_layers)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // LoRA Adapter section (Phase 10 v1.9.0)
                    SectionHeader(stringResource(R.string.section_lora), Icons.Default.AutoFixHigh)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.lora_use_adapter),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Switch(
                                    checked = loraEnabled,
                                    onCheckedChange = { viewModel.setLoraEnabled(it) }
                                )
                            }
                            Text(
                                text = stringResource(R.string.lora_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (loraEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))

                                // File picker for LoRA adapter
                                val loraPickerLauncher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    uri?.let {
                                        // Persist read permission
                                        context.contentResolver.takePersistableUriPermission(
                                            it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                        // Resolve to file path for llama.cpp
                                        val docId = DocumentsContract.getDocumentId(it)
                                        val resolvedPath = when {
                                            docId.startsWith("primary:") ->
                                                "/storage/emulated/0/${docId.removePrefix("primary:")}"
                                            else -> it.toString()
                                        }
                                        viewModel.setLoraPath(resolvedPath)
                                    }
                                }

                                Text(
                                    text = stringResource(R.string.lora_path),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            loraPickerLauncher.launch(arrayOf("*/*"))
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null,
                                            modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (loraPath.isBlank()) "Выбрать .gguf файл"
                                                   else loraPath.substringAfterLast("/"),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (loraPath.isNotBlank()) {
                                        IconButton(
                                            onClick = { viewModel.setLoraPath("") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Убрать",
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                                if (loraPath.isNotBlank()) {
                                    Text(
                                        text = loraPath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(R.string.lora_scale),
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "%.2f".format(loraScale),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Slider(
                                    value = loraScale,
                                    onValueChange = { viewModel.setLoraScale(it) },
                                    onValueChangeFinished = { viewModel.applyLoraScale() },
                                    valueRange = 0f..1f,
                                    steps = 9
                                )
                                Text(
                                    text = stringResource(R.string.lora_scale_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Git section (Phase 4 v1.3.0) + Biometric protection (Phase 8 v1.7.0)
                    SectionHeader(stringResource(R.string.section_git), Icons.Default.Code)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToGitCredentials() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.open_git_credentials),
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = stringResource(R.string.git_credentials_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Custom appearance section (Phase 12)
                    SectionHeader("Кастомизация", Icons.Default.AutoFixHigh)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                stringResource(R.string.color_presets),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CustomThemeColors.PRESETS.forEach { preset ->
                                    val presetAccent = CustomThemeColors.parseHex(preset.accent)
                                    OutlinedButton(
                                        onClick = { viewModel.applyColorPreset(preset) },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = CustomThemeColors.parseHex(preset.panel)
                                                ?: MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        if (presetAccent != null) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(presetAccent)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            preset.name,
                                            color = presetAccent ?: MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            // Background image
                            Text(
                                text = "Фон чата",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(onClick = {
                                    bgPickerLauncher.launch(arrayOf("image/*"))
                                }) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null,
                                        modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (customBgUri.isBlank()) "Выбрать изображение" else "Изменить")
                                }
                                if (customBgUri.isNotBlank()) {
                                    TextButton(onClick = { viewModel.setCustomBgUri("") }) {
                                        Text("Убрать")
                                    }
                                }
                            }

                            if (customBgUri.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Прозрачность: ${(customBgOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = customBgOpacity,
                                    onValueChange = { viewModel.setCustomBgOpacity(it) },
                                    valueRange = 0.05f..0.5f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Color pickers
                            Text(
                                text = "Цвета элементов",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ColorPalettePicker(
                                label = "Мои сообщения",
                                hex = customUserBubble,
                                onHexChange = { viewModel.setCustomUserBubble(it) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ColorPalettePicker(
                                label = "Ответы ассистента",
                                hex = customAssistantBubble,
                                onHexChange = { viewModel.setCustomAssistantBubble(it) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ColorPalettePicker(
                                label = "Акцент (кнопки, ссылки)",
                                hex = customAccent,
                                onHexChange = { viewModel.setCustomAccent(it) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ColorPalettePicker(
                                label = "Панели (поверхности)",
                                hex = customPanel,
                                onHexChange = { viewModel.setCustomPanel(it) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Extended colors — collapsible subsection
                            var extColorsExpanded by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { extColorsExpanded = !extColorsExpanded }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.section_colors_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    if (extColorsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            AnimatedVisibility(visible = extColorsExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ColorPalettePicker(
                                        label = stringResource(R.string.color_thermal_ok),
                                        hex = customThermalOk,
                                        onHexChange = { viewModel.setCustomThermalOk(it) }
                                    )
                                    ColorPalettePicker(
                                        label = stringResource(R.string.color_thermal_warn),
                                        hex = customThermalWarn,
                                        onHexChange = { viewModel.setCustomThermalWarn(it) }
                                    )
                                    ColorPalettePicker(
                                        label = stringResource(R.string.color_thermal_hot_label),
                                        hex = customThermalHot,
                                        onHexChange = { viewModel.setCustomThermalHot(it) }
                                    )
                                    ColorPalettePicker(
                                        label = stringResource(R.string.color_cpu_high),
                                        hex = customCpuHigh,
                                        onHexChange = { viewModel.setCustomCpuHigh(it) }
                                    )
                                    ColorPalettePicker(
                                        label = stringResource(R.string.color_gpu_bar),
                                        hex = customGpuBar,
                                        onHexChange = { viewModel.setCustomGpuBar(it) }
                                    )
                                    ColorPalettePicker(
                                        label = stringResource(R.string.color_backend_badge),
                                        hex = customBackendBadge,
                                        onHexChange = { viewModel.setCustomBackendBadge(it) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            TextButton(onClick = { viewModel.resetCustomTheme() }) {
                                Text("Сбросить всё к стандартным")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // About section
            SectionHeader(stringResource(R.string.section_about), Icons.Default.Info)

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.app_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Licenses button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToLicenses() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.licenses_title),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Privacy Policy
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.privacy_policy),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Terms of Service
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.terms_of_service),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog for cloning a remote git repo and setting it as the agent working directory.
 * v1.9.2
 */
@Composable
private fun CloneRepoDialog(
    cloneState: SettingsViewModel.CloneState,
    cloneUrl: String,
    onUrlChange: (String) -> Unit,
    onClone: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clone_repo)) },
        text = {
            Column {
                if (cloneState !is SettingsViewModel.CloneState.Done) {
                    OutlinedTextField(
                        value = cloneUrl,
                        onValueChange = onUrlChange,
                        label = { Text(stringResource(R.string.clone_url_hint)) },
                        placeholder = { Text("https://github.com/user/repo.git") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = cloneState is SettingsViewModel.CloneState.Idle ||
                                  cloneState is SettingsViewModel.CloneState.Error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.working_dir_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                when (cloneState) {
                    is SettingsViewModel.CloneState.Cloning -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.cloning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is SettingsViewModel.CloneState.Done -> {
                        Text(
                            text = stringResource(R.string.working_dir_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "✓ ${cloneState.path}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is SettingsViewModel.CloneState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠ ${cloneState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (cloneState) {
                is SettingsViewModel.CloneState.Done -> {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.done))
                    }
                }
                is SettingsViewModel.CloneState.Cloning -> {
                    Button(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.clone_start))
                    }
                }
                else -> {
                    Button(
                        onClick = onClone,
                        enabled = cloneUrl.isNotBlank()
                    ) {
                        Text(stringResource(R.string.clone_start))
                    }
                }
            }
        },
        dismissButton = {
            if (cloneState !is SettingsViewModel.CloneState.Cloning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/**
 * Dialog for downloading a model by direct URL.
 * Phase 10 (v1.9.0)
 */
@Composable
fun DownloadModelDialog(
    downloadState: DownloadState,
    onDownload: (url: String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_model_title)) },
        text = {
            Column {
                when (downloadState) {
                    is DownloadState.Idle -> {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text(stringResource(R.string.download_url_label)) },
                            placeholder = { Text(stringResource(R.string.download_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.download_url_example),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DownloadState.Downloading -> {
                        Text(
                            text = stringResource(R.string.download_progress_label, downloadState.filename),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val downloadedMb = downloadState.bytesDownloaded / (1024.0 * 1024.0)
                        val totalMb = downloadState.totalBytes / (1024.0 * 1024.0)
                        Text(
                            text = if (downloadState.totalBytes > 0)
                                stringResource(
                                    R.string.download_progress_bytes,
                                    downloadState.progress,
                                    downloadedMb,
                                    totalMb
                                )
                            else
                                "${downloadState.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is DownloadState.Done -> {
                        Text(
                            text = stringResource(R.string.download_done, downloadState.filename),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is DownloadState.Error -> {
                        Text(
                            text = stringResource(R.string.download_error, downloadState.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (downloadState) {
                is DownloadState.Idle -> {
                    Button(
                        onClick = { onDownload(url.trim()) },
                        enabled = url.isNotBlank()
                    ) {
                        Text(stringResource(R.string.download_start))
                    }
                }
                is DownloadState.Downloading -> {
                    Button(onClick = {
                        onCancel()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                is DownloadState.Done, is DownloadState.Error -> {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            }
        },
        dismissButton = {
            if (downloadState is DownloadState.Idle) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/**
 * Reusable slider with Auto/Manual toggle.
 * When auto=true, slider is disabled and shows "Auto" label.
 * Tapping "Auto" switches to manual mode revealing the slider.
 */
@Composable
private fun AutoManualSlider(
    label: String,
    value: Float,
    isAuto: Boolean,
    onAutoChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    hint: String,
    autoHint: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isAuto) "Auto" else valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isAuto) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isAuto,
                onCheckedChange = onAutoChange,
                modifier = Modifier.height(24.dp)
            )
        }
        if (!isAuto) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
        Text(
            text = if (isAuto) autoHint else hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * Reusable boolean toggle with Auto/Manual mode.
 * When auto=true, the toggle is disabled and system decides the value.
 */
@Composable
private fun AutoManualToggle(
    label: String,
    checked: Boolean,
    isAuto: Boolean,
    onAutoChange: (Boolean) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    hint: String,
    autoHint: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            if (!isAuto) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Auto",
                style = MaterialTheme.typography.bodySmall,
                color = if (isAuto) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = isAuto,
                onCheckedChange = onAutoChange,
                modifier = Modifier.height(24.dp)
            )
        }
        Text(
            text = if (isAuto) autoHint else hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * Styled section header with icon + accent line.
 */
@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
        )
    }
}

/**
 * Color palette picker with gradient area, hue slider, and HEX display.
 * Phase 12 → v0.11.0 rework: palette-style instead of raw HEX input.
 */
@Composable
private fun ColorPalettePicker(
    label: String,
    hex: String,
    onHexChange: (String) -> Unit
) {
    val parsedColor = CustomThemeColors.parseHex(hex)
    var expanded by remember { mutableStateOf(false) }

    // HSV state derived from current hex
    var hue by remember(hex) {
        mutableStateOf(
            parsedColor?.let { c ->
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(
                    (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv
                )
                hsv[0]
            } ?: 210f
        )
    }
    var saturation by remember(hex) {
        mutableStateOf(
            parsedColor?.let { c ->
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(
                    (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv
                )
                hsv[1]
            } ?: 0.7f
        )
    }
    var value by remember(hex) {
        mutableStateOf(
            parsedColor?.let { c ->
                val hsv = FloatArray(3)
                android.graphics.Color.RGBToHSV(
                    (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv
                )
                hsv[2]
            } ?: 0.8f
        )
    }

    fun commitColor() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        onHexChange("#FF%02X%02X%02X".format(r, g, b))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header row: swatch + label + expand toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(parsedColor ?: MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        Modifier.background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = if (parsedColor == null) listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                ) else listOf(parsedColor, parsedColor)
                            )
                        )
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = hex.ifBlank { "По умолчанию" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hex.isNotBlank()) {
                IconButton(onClick = { onHexChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Сбросить", modifier = Modifier.size(16.dp))
                }
            }
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // Saturation-Value gradient box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val x = (change.position.x / size.width).coerceIn(0f, 1f)
                                val y = (change.position.y / size.height).coerceIn(0f, 1f)
                                saturation = x
                                value = 1f - y
                                commitColor()
                            }
                        }
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                val x = (offset.x / size.width).coerceIn(0f, 1f)
                                val y = (offset.y / size.height).coerceIn(0f, 1f)
                                saturation = x
                                value = 1f - y
                                commitColor()
                            }
                        }
                        .drawBehind {
                            // White-to-hue horizontal gradient
                            val hueColor = android.graphics.Color.HSVToColor(
                                floatArrayOf(hue, 1f, 1f)
                            )
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.White,
                                        androidx.compose.ui.graphics.Color(hueColor)
                                    )
                                )
                            )
                            // Black overlay vertical gradient (top transparent → bottom black)
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        androidx.compose.ui.graphics.Color.Black
                                    )
                                )
                            )
                            // Selection circle
                            val cx = saturation * size.width
                            val cy = (1f - value) * size.height
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.White,
                                radius = 10.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.Black,
                                radius = 8.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Hue slider (rainbow bar)
                Text("Оттенок", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .drawBehind {
                            val rainbow = listOf(
                                androidx.compose.ui.graphics.Color.Red,
                                androidx.compose.ui.graphics.Color.Yellow,
                                androidx.compose.ui.graphics.Color.Green,
                                androidx.compose.ui.graphics.Color.Cyan,
                                androidx.compose.ui.graphics.Color.Blue,
                                androidx.compose.ui.graphics.Color.Magenta,
                                androidx.compose.ui.graphics.Color.Red
                            )
                            drawRect(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(rainbow)
                            )
                            // Thumb indicator
                            val thumbX = (hue / 360f) * size.width
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.White,
                                radius = 12.dp.toPx(),
                                center = androidx.compose.ui.geometry.Offset(thumbX, size.height / 2),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = ((change.position.x / size.width).coerceIn(0f, 1f)) * 360f
                                commitColor()
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                hue = ((offset.x / size.width).coerceIn(0f, 1f)) * 360f
                                commitColor()
                            }
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // HEX display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "HEX",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hex.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
