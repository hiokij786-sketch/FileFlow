package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.formatFileSize
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileFlowLogoMark
import com.example.ui.components.FileListItemCard
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import java.util.Calendar

private data class QuickActionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenPdfToImages: () -> Unit,
    onOpenImagesToPdf: () -> Unit,
    onOpenCompressPdf: () -> Unit,
    onOpenMergePdf: () -> Unit,
    onOpenSplitPdf: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCompressImage: () -> Unit,
    onOpenConverter: () -> Unit,
    onOpenExtractZip: () -> Unit,
    onOpenCreateZip: () -> Unit,
    onOpenPdfHub: () -> Unit,
    onOpenImageHub: () -> Unit,
    onOpenFilesTab: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFile: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val storageOverview by viewModel.storageOverview.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageOverview()
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    val quickActions = remember {
        listOf(
            QuickActionItem("pdf_to_img", "PDF to Images", "Export pages", Icons.Filled.Image, onOpenPdfToImages),
            QuickActionItem("img_to_pdf", "Images to PDF", "Build PDF", Icons.Filled.PictureAsPdf, onOpenImagesToPdf),
            QuickActionItem("compress_pdf", "Compress PDF", "Reduce size", Icons.Filled.Compress, onOpenCompressPdf),
            QuickActionItem("merge_pdf", "Merge PDF", "Combine docs", Icons.Filled.MergeType, onOpenMergePdf),
            QuickActionItem("split_pdf", "Split PDF", "Extract pages", Icons.Filled.CallSplit, onOpenSplitPdf),
            QuickActionItem("scan_doc", "Scan Document", "Camera & crop", Icons.Filled.DocumentScanner, onOpenScanner),
            QuickActionItem("compress_img", "Compress Image", "Optimize photos", Icons.Filled.Tune, onOpenCompressImage),
            QuickActionItem("convert_files", "Convert Files", "Formats & TXT", Icons.Filled.SwapHoriz, onOpenConverter),
            QuickActionItem("extract_zip", "Extract ZIP", "Unpack files", Icons.Filled.FolderZip, onOpenExtractZip),
            QuickActionItem("create_zip", "Create ZIP", "Archive bundle", Icons.Filled.Archive, onOpenCreateZip)
        )
    }

    val recentFiles = remember(allFiles) {
        allFiles.sortedByDescending { it.lastAccessedAt }.take(5)
    }
    val favoriteFiles = remember(allFiles) {
        allFiles.filter { it.isFavorite }.take(6)
    }
    val processedFiles = remember(allFiles) {
        allFiles.filter { it.isProcessed }.sortedByDescending { it.lastModified }.take(5)
    }

    val filteredActions = remember(searchQuery, quickActions) {
        if (searchQuery.isBlank()) emptyList()
        else quickActions.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.subtitle.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredFiles = remember(searchQuery, allFiles) {
        if (searchQuery.isBlank()) emptyList()
        else allFiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                (it.processTag?.contains(searchQuery, ignoreCase = true) == true)
        }.take(8)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Brand Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileFlowLogoMark(size = 40.dp, darkBackdrop = true)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FILEFLOW",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp
                            )
                        )
                        Text(
                            text = "$greeting • Everything Your Files Need",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(
                        onClick = onOpenStorage,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_storage_shortcut")
                    ) {
                        Icon(Icons.Filled.Storage, contentDescription = "Storage Overview")
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_shortcut")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        }

        // Universal Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tools, PDFs, images, archives...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_universal_search")
            )
        }

        // Search Results Mode
        if (searchQuery.isNotBlank()) {
            item {
                Text("Matching Tools (${filteredActions.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (filteredActions.isNotEmpty()) {
                items(filteredActions, key = { it.id }) { action ->
                    QuickActionRowCard(action)
                }
            }
            item {
                Text("Matching Files (${filteredFiles.size})", style = MaterialTheme.typography.titleMedium)
            }
            if (filteredFiles.isEmpty()) {
                item {
                    Text(
                        text = "No files in your library match \"$searchQuery\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredFiles, key = { it.id }) { file ->
                    FileListItemCard(
                        item = file,
                        onClick = { onOpenFile(file) },
                        onToggleFavorite = { viewModel.toggleFavorite(file) }
                    )
                }
            }
        } else {
            // Storage Snapshot Card
            item {
                val overview = storageOverview
                val totalDevice = (overview?.deviceTotalBytes ?: 1L).coerceAtLeast(1L)
                val availDevice = overview?.deviceAvailableBytes ?: 0L
                val usedFraction = ((totalDevice - availDevice).toFloat() / totalDevice.toFloat()).coerceIn(0f, 1f)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenStorage)
                        .testTag("home_storage_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Storage Overview", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "${formatFileSize(availDevice)} available on device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${allFiles.size} Library Files",
                                    style = MonoMetricsStyle,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { usedFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = GoldPrimary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Processed: ${formatFileSize(overview?.processedFilesBytes ?: 0L)}",
                                style = MonoMetricsStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Temp: ${formatFileSize(overview?.tempCacheBytes ?: 0L)}",
                                style = MonoMetricsStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 10 Quick Actions (2-column compact sleek grid)
            item {
                Text("Quick Actions", style = MaterialTheme.typography.titleLarge)
            }

            val rows = quickActions.chunked(2)
            items(rows) { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { action ->
                        QuickActionGridTile(
                            action = action,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Suite Shortcuts (PDF Tools, Image Tools, File Manager)
            item {
                Text("Tool Suites & Library", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SuiteShortcutCard(
                        title = "PDF Suite",
                        subtitle = "9 Master Tools",
                        icon = Icons.Filled.PictureAsPdf,
                        onClick = onOpenPdfHub,
                        modifier = Modifier.weight(1f)
                    )
                    SuiteShortcutCard(
                        title = "Image Studio",
                        subtitle = "Edit & Batch",
                        icon = Icons.Filled.PhotoLibrary,
                        onClick = onOpenImageHub,
                        modifier = Modifier.weight(1f)
                    )
                    SuiteShortcutCard(
                        title = "Files",
                        subtitle = "Organize All",
                        icon = Icons.Filled.FolderOpen,
                        onClick = onOpenFilesTab,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Favorites Section
            if (favoriteFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "Favorites", actionLabel = "See All", onAction = onOpenFilesTab)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favoriteFiles, key = { it.id }) { file ->
                            CompactRecentCard(item = file, onClick = { onOpenFile(file) })
                        }
                    }
                }
            }

            // Recently Processed Files
            if (processedFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "Recently Processed", actionLabel = "Library", onAction = onOpenFilesTab)
                }
                items(processedFiles, key = { "proc_${it.id}" }) { file ->
                    FileListItemCard(
                        item = file,
                        onClick = { onOpenFile(file) },
                        onToggleFavorite = { viewModel.toggleFavorite(file) }
                    )
                }
            }

            // Recent Files
            item {
                SectionHeader(title = "Recent Files", actionLabel = "Browse", onAction = onOpenFilesTab)
            }
            if (recentFiles.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.FolderOpen,
                        title = "Your FILEFLOW Workspace is Ready",
                        description = "Tap any Quick Action above, scan a document, or open Files to import PDFs, images, and archives.",
                        actionLabel = "Open File Manager",
                        onAction = onOpenFilesTab
                    )
                }
            } else {
                items(recentFiles, key = { "rec_${it.id}" }) { file ->
                    FileListItemCard(
                        item = file,
                        onClick = { onOpenFile(file) },
                        onToggleFavorite = { viewModel.toggleFavorite(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionGridTile(
    action: QuickActionItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable(onClick = action.onClick)
            .testTag("quick_action_${action.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.title,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = action.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickActionRowCard(action: QuickActionItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = action.onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(action.title, style = MaterialTheme.typography.titleSmall)
                Text(action.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SuiteShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = GoldPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondary,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.75f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompactRecentCard(
    item: FileItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(164.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.category.label,
                style = MaterialTheme.typography.labelSmall,
                color = GoldPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatFileSize(item.sizeBytes),
                style = MonoMetricsStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
