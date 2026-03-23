package com.quantma.lite.ui.models

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.quantma.lite.R
import com.quantma.lite.data.download.DownloadState
import com.quantma.lite.data.model.ModelCategory
import com.quantma.lite.data.model.RecommendedModel
import com.quantma.lite.data.model.RecommendedModels

/**
 * Screen showing a catalog of popular GGUF models with download buttons.
 * Phase 9 (v1.9.1) → v2.0: categories, detailed info dialog, cancel support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelCatalogScreen(
    viewModel: ModelCatalogViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val downloadStates by viewModel.downloadStates.collectAsState()
    var selectedFilter by remember { mutableStateOf<ModelCategory?>(null) }
    var modelToConfirm by remember { mutableStateOf<RecommendedModel?>(null) }

    val filteredModels = if (selectedFilter != null) {
        RecommendedModels.list.filter { it.category == selectedFilter }
    } else {
        RecommendedModels.list
    }

    // ── Confirmation dialog ──
    modelToConfirm?.let { model ->
        ModelDetailDialog(
            model = model,
            onConfirm = {
                viewModel.startDownload(model)
                modelToConfirm = null
            },
            onDismiss = { modelToConfirm = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.model_catalog)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Category filter chips
            item {
                CategoryFilterRow(
                    selected = selectedFilter,
                    onSelect = { selectedFilter = if (selectedFilter == it) null else it }
                )
            }

            // Model cards grouped by category
            val grouped = filteredModels.groupBy { it.category }
            ModelCategory.entries.forEach { category ->
                val models = grouped[category] ?: return@forEach
                item {
                    Text(
                        text = categoryTitle(category),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(models, key = { it.filename }) { model ->
                    ModelCard(
                        model = model,
                        state = downloadStates[model.filename],
                        onDownloadClick = { modelToConfirm = model },
                        onCancelClick = { viewModel.cancelDownload(model) }
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ── Category filter row ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    selected: ModelCategory?,
    onSelect: (ModelCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModelCategory.entries.forEach { cat ->
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = { Text(categoryChipLabel(cat)) }
            )
        }
    }
}

// ── Model card ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelCard(
    model: RecommendedModel,
    state: DownloadState?,
    onDownloadClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: name + button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                when (state) {
                    is DownloadState.Done -> {
                        Text(
                            text = stringResource(R.string.catalog_done),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is DownloadState.Downloading -> {
                        OutlinedButton(onClick = onCancelClick) {
                            Text(stringResource(R.string.catalog_cancel))
                        }
                    }
                    else -> {
                        Button(onClick = onDownloadClick) {
                            Text(stringResource(R.string.catalog_download))
                        }
                    }
                }
            }

            // Specs row
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SpecLabel(label = model.paramCount)
                SpecLabel(label = model.quantization)
                SpecLabel(label = model.sizeLabel)
                SpecLabel(label = "RAM ${model.ramRequired}")
            }

            // Capability chips
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                model.capabilities.take(4).forEach { cap ->
                    AssistChip(
                        onClick = { },
                        label = {
                            Text(
                                text = cap,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.height(28.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }

            // Download progress
            if (state is DownloadState.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                val progressFraction = if (state.progress > 0) state.progress / 100f else 0f
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                val downloadedMb = state.bytesDownloaded / 1_048_576f
                val totalMb = state.totalBytes / 1_048_576f
                Text(
                    text = if (state.totalBytes > 0)
                        "${state.progress}% \u00b7 %.1f MB / %.1f MB".format(downloadedMb, totalMb)
                    else
                        stringResource(R.string.catalog_downloading),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error
            if (state is DownloadState.Error) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.catalog_error) + ": ${state.message}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SpecLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

// ── Confirmation dialog ─────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelDetailDialog(
    model: RecommendedModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = model.name,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                // Category badge
                Text(
                    text = categoryTitle(model.category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Detailed description
                Text(
                    text = model.detailedDescription,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Specs grid
                Text(
                    text = stringResource(R.string.catalog_specs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                SpecRow(stringResource(R.string.catalog_spec_params), model.paramCount)
                SpecRow(stringResource(R.string.catalog_spec_quant), model.quantization)
                SpecRow(stringResource(R.string.catalog_spec_size), model.sizeLabel)
                SpecRow(stringResource(R.string.catalog_spec_ram), model.ramRequired)

                if (model.mmProjUrl != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.catalog_vision_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Capabilities
                Text(
                    text = stringResource(R.string.catalog_capabilities),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    model.capabilities.forEach { cap ->
                        AssistChip(
                            onClick = { },
                            label = {
                                Text(
                                    text = cap,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.catalog_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.catalog_cancel))
            }
        }
    )
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────────

@Composable
private fun categoryTitle(category: ModelCategory): String = when (category) {
    ModelCategory.COMPACT -> stringResource(R.string.catalog_cat_compact)
    ModelCategory.CODING -> stringResource(R.string.catalog_cat_coding)
    ModelCategory.GENERAL -> stringResource(R.string.catalog_cat_general)
    ModelCategory.MULTIMODAL -> stringResource(R.string.catalog_cat_multimodal)
}

@Composable
private fun categoryChipLabel(category: ModelCategory): String = when (category) {
    ModelCategory.COMPACT -> stringResource(R.string.catalog_chip_compact)
    ModelCategory.CODING -> stringResource(R.string.catalog_chip_coding)
    ModelCategory.GENERAL -> stringResource(R.string.catalog_chip_general)
    ModelCategory.MULTIMODAL -> stringResource(R.string.catalog_chip_multimodal)
}
