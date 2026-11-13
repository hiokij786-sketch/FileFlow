package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.LinkedSafFolderEntity
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.formatFileSize
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileCategoryBadge
import com.example.ui.components.FileListItemCard
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryTab(val label: String) {
    ALL("All Files"),
    PROCESSED("Processed"),
    RECENT("Recent"),
    FAVORITES("Favorites"),
    FOLDERS("Folders")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    viewModel: MainViewModel,
    onOpenFile: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val linkedFolders by viewModel.linkedFolders.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(LibraryTab.ALL) }
    var selectedCategory by remember { mutableStateOf(FileCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFolderFilter by remember { mutableStateOf<String?>(null) }
    var activeSafFolder by remember { mutableStateOf<LinkedSafFolderEntity?>(null) }
    var safFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var safError by remember { mutableStateOf<String?>(null) }

    var showSortMenu by remember { mutableStateOf(false) }
    var isMultiSelect by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var activeActionItem by remember { mutableStateOf<FileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var folderPickTarget by remember { mutableStateOf<Pair<FileItem, Boolean>?>(null) } // Boolean: isMove
    var deleteTarget by remember { mutableStateOf<List<FileItem>?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItem?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val target = activeFolderFilter ?: "Imported"
                val imported = viewModel.importUris(uris, target)
                Toast.makeText(context, "Imported ${imported.size} file(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val linkFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.linkSafFolder(uri) { res ->
                res.onSuccess {
                    Toast.makeText(context, "Linked ${it.displayName}", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(context, "Could not link folder: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(activeSafFolder) {
        val saf = activeSafFolder
        if (saf != null) {
            safError = null
            viewModel.workspaceRepo.listSafFolderFiles(saf.treeUri)
                .onSuccess { safFiles = it }
                .onFailure { safError = it.message; safFiles = emptyList() }
        }
    }

    val displayedFiles = remember(
        allFiles, safFiles, activeSafFolder, activeFolderFilter,
        selectedTab, selectedCategory, searchQuery, prefs.sortField, prefs.sortOrder
    ) {
        val baseList = when {
            activeSafFolder != null -> safFiles
            activeFolderFilter != null -> allFiles.filter { it.folderName == activeFolderFilter }
            selectedTab == LibraryTab.PROCESSED -> allFiles.filter { it.isProcessed }
            selectedTab == LibraryTab.RECENT -> allFiles.sortedByDescending { it.lastAccessedAt }.take(25)
            selectedTab == LibraryTab.FAVORITES -> allFiles.filter { it.isFavorite }
            else -> allFiles
        }
        viewModel.filterAndSort(baseList, searchQuery, selectedCategory, prefs.sortField, prefs.sortOrder)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activeSafFolder?.displayName ?: activeFolderFilter ?: "Files & Library",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${displayedFiles.size} item(s) • On-device workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                if (activeFolderFilter != null || activeSafFolder != null) {
                    TextButton(onClick = { activeFolderFilter = null; activeSafFolder = null }) {
                        Text("Back to All")
                    }
                }
                IconButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "Create folder")
                }
                IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = "Import files")
                }
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Filled.Sort, contentDescription = "Sort files")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortField.entries.forEach { field ->
                            DropdownMenuItem(
                                text = {
                                    val arrow = if (prefs.sortField == field) {
                                        if (prefs.sortOrder == SortOrder.ASC) " ↑" else " ↓"
                                    } else ""
                                    Text("Sort by ${field.label}$arrow")
                                },
                                onClick = {
                                    val nextOrder = if (prefs.sortField == field && prefs.sortOrder == SortOrder.DESC) {
                                        SortOrder.ASC
                                    } else {
                                        SortOrder.DESC
                                    }
                                    viewModel.setSortPreferences(field, nextOrder)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = { viewModel.setGridView(!prefs.isGridView) }) {
                    Icon(
                        imageVector = if (prefs.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Toggle grid or list view"
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search files by name or tag...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                    IconButton(onClick = {
                        isMultiSelect = !isMultiSelect
                        if (!isMultiSelect) selectedIds = emptySet()
                    }) {
                        Icon(
                            Icons.Filled.CheckBox,
                            contentDescription = "Multi-select",
                            tint = if (isMultiSelect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(8.dp))

        // Multi-Select Action Bar
        if (isMultiSelect) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${selectedIds.size} selected", style = MaterialTheme.typography.titleSmall)
                    Row {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == displayedFiles.size) emptySet()
                            else displayedFiles.map { it.id }.toSet()
                        }) { Text(if (selectedIds.size == displayedFiles.size) "None" else "All") }

                        if (selectedIds.isNotEmpty() && activeSafFolder == null) {
                            IconButton(onClick = {
                                deleteTarget = displayedFiles.filter { it.id in selectedIds }
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                            }
                        }
                    }
                }
            }
        }

        // Library Tabs
        if (activeFolderFilter == null && activeSafFolder == null) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                LibraryTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }
        }

        // Category Filter Chips (when not inside Folder overview tab)
        if (selectedTab != LibraryTab.FOLDERS || activeFolderFilter != null || activeSafFolder != null) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FileCategory.entries) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label) }
                    )
                }
            }
        }

        // Content Body
        if (selectedTab == LibraryTab.FOLDERS && activeFolderFilter == null && activeSafFolder == null) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("FILEFLOW Folders", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showCreateFolderDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("New Folder")
                        }
                    }
                }
                items(folders, key = { it.path }) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { activeFolderFilter = folder.name },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${folder.fileCount} files • ${formatFileSize(folder.totalBytes)}",
                                    style = MonoMetricsStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Linked Device Folders (SAF)", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { linkFolderLauncher.launch(null) }) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Link Folder")
                        }
                    }
                }
                if (linkedFolders.isEmpty()) {
                    item {
                        Text(
                            text = "Grant access to specific device folders (e.g. Downloads or Documents) safely without broad storage permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(linkedFolders, key = { it.treeUri }) { saf ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeSafFolder = saf },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(saf.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = { viewModel.unlinkSafFolder(saf.treeUri) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Unlink folder")
                                }
                            }
                        }
                    }
                }
            }
        } else if (displayedFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                EmptyStatePanel(
                    icon = Icons.Filled.FolderOpen,
                    title = if (safError != null) "Folder Unavailable" else "No Files Here Yet",
                    description = safError ?: "Import files from your device or create documents using FILEFLOW tools.",
                    actionLabel = "Import from Device",
                    onAction = { importLauncher.launch(arrayOf("*/*")) }
                )
            }
        } else if (prefs.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedFiles, key = { it.id }) { item ->
                    val isSel = item.id in selectedIds
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isMultiSelect) {
                                    selectedIds = if (isSel) selectedIds - item.id else selectedIds + item.id
                                } else {
                                    onOpenFile(item)
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            if (item.category == FileCategory.IMAGE && !item.isExternalSaf) {
                                AsyncImage(
                                    model = File(item.path),
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.25f)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.25f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    FileCategoryBadge(item.category)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(formatFileSize(item.sizeBytes), style = MonoMetricsStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = { activeActionItem = item }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Info, contentDescription = "Options", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(displayedFiles, key = { it.id }) { item ->
                    val isSel = item.id in selectedIds
                    FileListItemCard(
                        item = item,
                        isSelected = isSel,
                        onClick = {
                            if (isMultiSelect) {
                                selectedIds = if (isSel) selectedIds - item.id else selectedIds + item.id
                            } else {
                                onOpenFile(item)
                            }
                        },
                        onToggleFavorite = if (item.isExternalSaf) null else { { viewModel.toggleFavorite(item) } },
                        onMoreClick = { activeActionItem = item }
                    )
                }
            }
        }
    }

    // File Actions Bottom Sheet
    activeActionItem?.let { item ->
        ModalBottomSheet(onDismissRequest = { activeActionItem = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Open / Preview") },
                    leadingContent = { Icon(Icons.Filled.OpenInNew, contentDescription = null) },
                    modifier = Modifier.clickable { activeActionItem = null; onOpenFile(item) }
                )
                ListItem(
                    headlineContent = { Text("Share") },
                    leadingContent = { Icon(Icons.Filled.Share, contentDescription = null) },
                    modifier = Modifier.clickable { activeActionItem = null; viewModel.shareFile(item) }
                )
                if (!item.isExternalSaf) {
                    ListItem(
                        headlineContent = { Text("Export to Device Storage") },
                        leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                        modifier = Modifier.clickable { activeActionItem = null; onRequestExport(item) }
                    )
                    ListItem(
                        headlineContent = { Text(if (item.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                        leadingContent = { Icon(Icons.Filled.Star, contentDescription = null) },
                        modifier = Modifier.clickable { activeActionItem = null; viewModel.toggleFavorite(item) }
                    )
                    ListItem(
                        headlineContent = { Text("Rename") },
                        leadingContent = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                        modifier = Modifier.clickable {
                            activeActionItem = null
                            renameTarget = item
                            renameInput = item.name
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Copy to Folder") },
                        leadingContent = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                        modifier = Modifier.clickable {
                            activeActionItem = null
                            folderPickTarget = item to false
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Move to Folder") },
                        leadingContent = { Icon(Icons.Filled.DriveFileMove, contentDescription = null) },
                        modifier = Modifier.clickable {
                            activeActionItem = null
                            folderPickTarget = item to true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("File Details") },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                        modifier = Modifier.clickable { activeActionItem = null; detailsTarget = item }
                    )
                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            activeActionItem = null
                            deleteTarget = listOf(item)
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createFolder(newFolderName) { res ->
                        res.onSuccess {
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }.onFailure {
                            Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Filename") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameFile(target, renameInput) { res ->
                        res.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
                        renameTarget = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    folderPickTarget?.let { (target, isMove) ->
        AlertDialog(
            onDismissRequest = { folderPickTarget = null },
            title = { Text(if (isMove) "Move to Folder" else "Copy to Folder") },
            text = {
                Column {
                    folders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.clickable {
                                if (isMove) {
                                    viewModel.moveFile(target, folder.name) {
                                        Toast.makeText(context, "Moved to ${folder.name}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    viewModel.copyFile(target, folder.name) {
                                        Toast.makeText(context, "Copied to ${folder.name}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                folderPickTarget = null
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { folderPickTarget = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget?.let { targets ->
        ConfirmDeleteDialog(
            title = "Delete ${targets.size} File(s)?",
            message = "This permanently removes the selected file(s) from your FILEFLOW library. This action cannot be undone.",
            onConfirm = {
                targets.forEach { viewModel.deleteFile(it) }
                selectedIds = emptySet()
                isMultiSelect = false
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    detailsTarget?.let { item ->
        val modified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(item.lastModified))
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text("File Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Name: ${item.name}", style = MaterialTheme.typography.bodyMedium)
                    Text("Size: ${formatFileSize(item.sizeBytes)}", style = MonoMetricsStyle)
                    Text("Category: ${item.category.label}", style = MaterialTheme.typography.bodySmall)
                    Text("Folder: ${item.folderName}", style = MaterialTheme.typography.bodySmall)
                    Text("Modified: $modified", style = MaterialTheme.typography.bodySmall)
                    Text("MIME: ${item.mimeType}", style = MaterialTheme.typography.bodySmall)
                    if (!item.processTag.isNullOrBlank()) {
                        Text("Process Tag: ${item.processTag}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsTarget = null }) { Text("Close") }
            }
        )
    }
}
