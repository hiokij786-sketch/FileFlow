package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.ZipEntryInfo
import com.example.data.model.formatFileSize
import com.example.engine.ZipEngine
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ZipToolsScreen(
    initialMode: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var isCreateMode by remember(initialMode) { mutableStateOf(initialMode == "create") }

    // Extract State
    var selectedZip by remember { mutableStateOf<FileItem?>(null) }
    var zipEntries by remember { mutableStateOf<List<ZipEntryInfo>>(emptyList()) }
    var selectedEntryNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var renameMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var renameEntryTarget by remember { mutableStateOf<ZipEntryInfo?>(null) }
    var renameEntryInput by remember { mutableStateOf("") }
    var extractDestFolder by remember { mutableStateOf("Archives") }
    var extractStatus by remember { mutableStateOf(ToolStatusState<List<FileItem>>()) }

    // Create State
    var filesToZip by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var archiveName by remember { mutableStateOf("Archive_${System.currentTimeMillis() % 10000}.zip") }
    var createDestFolder by remember(prefs.defaultOutputFolder) { mutableStateOf(prefs.defaultOutputFolder) }
    var createStatus by remember { mutableStateOf(ToolStatusState<FileItem>()) }

    var showPicker by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selectedZip) {
        val zip = selectedZip
        if (zip != null) {
            extractStatus = ToolStatusState()
            ZipEngine.listZipEntries(File(zip.path))
                .onSuccess { list ->
                    zipEntries = list
                    selectedEntryNames = list.filter { !it.isDirectory }.map { it.name }.toSet()
                }
                .onFailure { err ->
                    zipEntries = emptyList()
                    selectedEntryNames = emptySet()
                    extractStatus = ToolStatusState(
                        ProcessStep.ERROR,
                        errorMessage = "Invalid or unsupported archive: ${err.message}"
                    )
                }
        } else {
            zipEntries = emptyList()
            selectedEntryNames = emptySet()
        }
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "ZIP & Archives",
                subtitle = "Inspect, extract safely, or create ZIPs",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isCreateMode,
                        onClick = { isCreateMode = false },
                        label = { Text("Extract ZIP") },
                        leadingIcon = { Icon(Icons.Filled.FolderZip, contentDescription = null) }
                    )
                    FilterChip(
                        selected = isCreateMode,
                        onClick = { isCreateMode = true },
                        label = { Text("Create ZIP") },
                        leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) }
                    )
                }
            }

            if (!isCreateMode) {
                item {
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedZip?.let { "${it.name} (${formatFileSize(it.sizeBytes)})" } ?: "Select ZIP Archive")
                    }
                }

                if (selectedZip == null) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Filled.FolderZip,
                            title = "Browse & Extract ZIP Archives",
                            description = "Inspect archive contents, choose specific files, rename extracted outputs, and unpack safely with path-traversal protection.",
                            actionLabel = "Select ZIP File",
                            onAction = { showPicker = true }
                        )
                    }
                } else {
                    if (zipEntries.isNotEmpty()) {
                        val fileEntries = zipEntries.filter { !it.isDirectory }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Contents (${selectedEntryNames.size}/${fileEntries.size} selected)", style = MaterialTheme.typography.titleSmall)
                                TextButton(onClick = {
                                    selectedEntryNames = if (selectedEntryNames.size == fileEntries.size) emptySet()
                                    else fileEntries.map { it.name }.toSet()
                                }) {
                                    Text(if (selectedEntryNames.size == fileEntries.size) "Deselect All" else "Select All")
                                }
                            }
                        }

                        items(fileEntries, key = { it.name }) { entry ->
                            val isSel = entry.name in selectedEntryNames
                            val displayName = renameMap[entry.name] ?: entry.name
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedEntryNames = if (isSel) selectedEntryNames - entry.name else selectedEntryNames + entry.name
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSel,
                                        onCheckedChange = { checked ->
                                            selectedEntryNames = if (checked) selectedEntryNames + entry.name else selectedEntryNames - entry.name
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(formatFileSize(entry.sizeBytes), style = MonoMetricsStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = {
                                        renameEntryTarget = entry
                                        renameEntryInput = renameMap[entry.name] ?: entry.name.substringAfterLast('/')
                                    }) {
                                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename extracted file")
                                    }
                                }
                            }
                        }

                        item {
                            Text("Destination Folder", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val folderNames = (folders.map { it.name } + "Archives").distinct()
                                items(folderNames) { folder ->
                                    FilterChip(
                                        selected = extractDestFolder == folder,
                                        onClick = { extractDestFolder = folder },
                                        label = { Text(folder) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val zip = selectedZip ?: return@Button
                                    activeJob?.cancel()
                                    activeJob = scope.launch {
                                        extractStatus = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Validating archive paths...")
                                        val destSubDir = File(
                                            viewModel.workspaceRepo.getTargetFolder(extractDestFolder),
                                            File(zip.path).nameWithoutExtension
                                        )
                                        ZipEngine.extractZip(
                                            zipFile = File(zip.path),
                                            destDir = destSubDir,
                                            selectedEntries = selectedEntryNames,
                                            renamedFileMap = renameMap
                                        ) { p, m -> extractStatus = ToolStatusState(ProcessStep.PROCESSING, p, m) }
                                            .onSuccess { files ->
                                                val items = files.map { viewModel.workspaceRepo.recordProcessedFile(it, "Extracted from ZIP") }
                                                extractStatus = ToolStatusState(ProcessStep.SUCCESS, 1f, "Extracted ${items.size} file(s)", result = items)
                                            }
                                            .onFailure { err ->
                                                if (err is kotlinx.coroutines.CancellationException) {
                                                    extractStatus = ToolStatusState(ProcessStep.CANCELLED)
                                                } else {
                                                    extractStatus = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                                }
                                            }
                                    }
                                },
                                enabled = selectedEntryNames.isNotEmpty() && extractStatus.step != ProcessStep.PROCESSING,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Extract ${selectedEntryNames.size} Selected File(s)")
                            }
                        }
                    }
                }

                item {
                    ToolProgressAndResultCard(
                        state = extractStatus,
                        onCancel = { activeJob?.cancel() },
                        onDismissError = { extractStatus = ToolStatusState() }
                    ) { extracted ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            extracted.take(5).forEach { f ->
                                OutputFileActionsCard(
                                    title = "Extracted ${f.name}",
                                    file = f,
                                    onSaveToDevice = { onRequestExport(f) },
                                    onShare = { viewModel.shareFile(f) },
                                    onOpenExternal = { viewModel.openExternal(f) }
                                )
                            }
                            if (extracted.size > 5) {
                                Text("All ${extracted.size} extracted files saved to $extractDestFolder.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                // CREATE ZIP MODE
                item {
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Files to Archive (${filesToZip.size})")
                    }
                }

                if (filesToZip.isEmpty()) {
                    item {
                        EmptyStatePanel(
                            icon = Icons.Filled.Archive,
                            title = "Create a ZIP Archive",
                            description = "Bundle PDFs, images, and documents into a compressed .zip archive ready to save or share.",
                            actionLabel = "Add Files",
                            onAction = { showPicker = true }
                        )
                    }
                } else {
                    items(filesToZip, key = { it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(formatFileSize(item.sizeBytes), style = MonoMetricsStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { filesToZip = filesToZip.filterNot { it.id == item.id } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = archiveName,
                            onValueChange = { archiveName = it },
                            label = { Text("Archive Filename") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                activeJob?.cancel()
                                activeJob = scope.launch {
                                    createStatus = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Compressing files...")
                                    val safeName = if (archiveName.endsWith(".zip", true)) archiveName else "$archiveName.zip"
                                    val outZip = viewModel.workspaceRepo.buildUniqueOutputFile(createDestFolder, safeName)
                                    ZipEngine.createZip(
                                        inputFiles = filesToZip.map { File(it.path) },
                                        outputZipFile = outZip
                                    ) { p, m -> createStatus = ToolStatusState(ProcessStep.PROCESSING, p, m) }
                                        .onSuccess { z ->
                                            val record = viewModel.workspaceRepo.recordProcessedFile(z, "ZIP Archive")
                                            createStatus = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = record)
                                        }
                                        .onFailure { err ->
                                            if (err is kotlinx.coroutines.CancellationException) {
                                                outZip.delete()
                                                createStatus = ToolStatusState(ProcessStep.CANCELLED)
                                            } else {
                                                createStatus = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                            }
                                        }
                                }
                            },
                            enabled = createStatus.step != ProcessStep.PROCESSING,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create ZIP (${filesToZip.size} files)")
                        }
                    }
                }

                item {
                    ToolProgressAndResultCard(
                        state = createStatus,
                        onCancel = { activeJob?.cancel() },
                        onDismissError = { createStatus = ToolStatusState() }
                    ) { outZip ->
                        OutputFileActionsCard(
                            title = "ZIP Archive Created",
                            file = outZip,
                            onSaveToDevice = { onRequestExport(outZip) },
                            onShare = { viewModel.shareFile(outZip) },
                            onOpenExternal = { viewModel.openExternal(outZip) }
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Standard .zip archives are supported. Proprietary .rar and .7z formats require external unpackers and are not supported.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        if (!isCreateMode) {
            WorkspaceOrDeviceFilePickerSheet(
                title = "Select ZIP Archive",
                workspaceFiles = allFiles,
                allowedCategories = setOf(FileCategory.ARCHIVE),
                mimeFilter = arrayOf("application/zip", "application/x-zip-compressed"),
                allowMultiple = false,
                onImportUris = { uris -> viewModel.importUris(uris, "Archives") },
                onFilesPicked = { picked -> selectedZip = picked.firstOrNull() },
                onDismiss = { showPicker = false }
            )
        } else {
            WorkspaceOrDeviceFilePickerSheet(
                title = "Add Files to Archive",
                workspaceFiles = allFiles,
                allowedCategories = setOf(FileCategory.ALL),
                mimeFilter = arrayOf("*/*"),
                allowMultiple = true,
                onImportUris = { uris -> viewModel.importUris(uris, "Imported") },
                onFilesPicked = { picked -> filesToZip = (filesToZip + picked).distinctBy { it.id } },
                onDismiss = { showPicker = false }
            )
        }
    }

    renameEntryTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { renameEntryTarget = null },
            title = { Text("Rename Extracted File") },
            text = {
                OutlinedTextField(
                    value = renameEntryInput,
                    onValueChange = { renameEntryInput = it },
                    label = { Text("Output Filename") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val clean = viewModel.workspaceRepo.sanitizeFileName(renameEntryInput)
                    if (clean.isNotBlank()) {
                        renameMap = renameMap + (entry.name to clean)
                    }
                    renameEntryTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameEntryTarget = null }) { Text("Cancel") }
            }
        )
    }
}
