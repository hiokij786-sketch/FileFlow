package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileItem
import com.example.data.model.formatFileSize
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel

@Composable
fun StorageOverviewScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val overview by viewModel.storageOverview.collectAsStateWithLifecycle()
    var confirmClearTemp by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageOverview()
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Storage Overview",
                subtitle = "Accessible space & FILEFLOW library usage",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        val data = overview
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (data != null) {
                val total = data.deviceTotalBytes.coerceAtLeast(1L)
                val usedFraction = ((total - data.deviceAvailableBytes).toFloat() / total.toFloat()).coerceIn(0f, 1f)

                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Accessible Device Storage", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${formatFileSize(data.deviceAvailableBytes)} free of ${formatFileSize(data.deviceTotalBytes)}",
                                style = MonoMetricsStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { usedFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = GoldPrimary
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("FILEFLOW Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatFileSize(data.workspaceTotalBytes), style = MonoMetricsStyle)
                                }
                                Column {
                                    Text("Processed Files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatFileSize(data.processedFilesBytes), style = MonoMetricsStyle)
                                }
                                Column {
                                    Text("Temporary Cache", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatFileSize(data.tempCacheBytes), style = MonoMetricsStyle)
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { confirmClearTemp = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Clear Temporary Files (${formatFileSize(data.tempCacheBytes)})")
                    }
                }

                item {
                    Text("Breakdown by Category", style = MaterialTheme.typography.titleMedium)
                }

                items(data.categoryBreakdown.entries.toList()) { (category, bytes) ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FileCategoryBadge(category)
                            Spacer(Modifier.width(12.dp))
                            Text(category.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(formatFileSize(bytes), style = MonoMetricsStyle)
                        }
                    }
                }

                item {
                    Text("Large Files (> 1 MB)", style = MaterialTheme.typography.titleMedium)
                }

                if (data.largeFiles.isEmpty()) {
                    item {
                        Text(
                            text = "No files larger than 1 MB in your FILEFLOW library.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(data.largeFiles, key = { it.id }) { file ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text("${formatFileSize(file.sizeBytes)} • ${file.folderName}", style = MonoMetricsStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { deleteCandidate = file }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete large file", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClearTemp) {
        ConfirmDeleteDialog(
            title = "Clear Temporary Files?",
            message = "This removes temporary preview and conversion scratch files only. Your saved documents and library files are untouched.",
            confirmLabel = "Clear Cache",
            onConfirm = {
                viewModel.clearTemporaryCache { cleared ->
                    Toast.makeText(context, "Cleared ${formatFileSize(cleared)} of temporary files", Toast.LENGTH_SHORT).show()
                }
                confirmClearTemp = false
            },
            onDismiss = { confirmClearTemp = false }
        )
    }

    deleteCandidate?.let { file ->
        ConfirmDeleteDialog(
            title = "Delete ${file.name}?",
            message = "This permanently deletes ${formatFileSize(file.sizeBytes)} from your FILEFLOW library.",
            onConfirm = {
                viewModel.deleteFile(file)
                deleteCandidate = null
            },
            onDismiss = { deleteCandidate = null }
        )
    }
}
