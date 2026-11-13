package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompressionLevel
import com.example.data.model.CompressionReport
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.formatFileSize
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.PdfThumbnailView
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfMergeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPdfs by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var outputFileName by remember { mutableStateOf("Merged_${System.currentTimeMillis() % 10000}.pdf") }
    var targetFolder by remember(prefs.defaultOutputFolder) { mutableStateOf(prefs.defaultOutputFolder) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<FileItem>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Merge PDFs",
                subtitle = "Combine & reorder multiple PDFs",
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
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("merge_add_pdfs_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add PDF Documents (${selectedPdfs.size} selected)")
                }
            }

            if (selectedPdfs.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Select PDFs to Merge",
                        description = "Add 2 or more PDF files, adjust their order, and merge them into a single clean document.",
                        actionLabel = "Select PDFs",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                itemsIndexed(selectedPdfs, key = { idx, item -> "${idx}_${item.id}" }) { index, item ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfThumbnailView(
                                file = File(item.path),
                                pageIndex = 0,
                                pdfEngine = viewModel.pdfEngine,
                                modifier = Modifier.size(48.dp, 62.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${index + 1}. ${item.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(formatFileSize(item.sizeBytes), style = MonoMetricsStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val mutable = selectedPdfs.toMutableList()
                                    val moved = mutable.removeAt(index)
                                    mutable.add(index - 1, moved)
                                    selectedPdfs = mutable
                                }
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(
                                enabled = index < selectedPdfs.lastIndex,
                                onClick = {
                                    val mutable = selectedPdfs.toMutableList()
                                    val moved = mutable.removeAt(index)
                                    mutable.add(index + 1, moved)
                                    selectedPdfs = mutable
                                }
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                            }
                            IconButton(onClick = {
                                selectedPdfs = selectedPdfs.filterIndexed { i, _ -> i != index }
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("Output Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Destination Folder", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val folderNames = (folders.map { it.name } + prefs.defaultOutputFolder).distinct()
                        items(folderNames) { folder ->
                            FilterChip(
                                selected = targetFolder == folder,
                                onClick = { targetFolder = folder },
                                label = { Text(folder) }
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Starting merge...")
                                val safeName = if (outputFileName.endsWith(".pdf", true)) outputFileName else "$outputFileName.pdf"
                                val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(targetFolder, safeName)
                                val res = viewModel.pdfEngine.mergePdfs(
                                    inputFiles = selectedPdfs.map { File(it.path) },
                                    outputFile = outFile
                                ) { p, msg ->
                                    status = ToolStatusState(ProcessStep.PROCESSING, p, msg)
                                }
                                res.onSuccess { merged ->
                                    val record = viewModel.workspaceRepo.recordProcessedFile(merged, "Merged PDF")
                                    status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Merged successfully", result = record)
                                }.onFailure { err ->
                                    if (err is kotlinx.coroutines.CancellationException) {
                                        outFile.delete()
                                        status = ToolStatusState(ProcessStep.CANCELLED)
                                    } else {
                                        status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                    }
                                }
                            }
                        },
                        enabled = selectedPdfs.size >= 2 && status.step != ProcessStep.PROCESSING,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_execute_button")
                    ) {
                        Text("Merge ${selectedPdfs.size} PDFs")
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onCancel = { activeJob?.cancel() },
                    onDismissError = { status = ToolStatusState() }
                ) { outItem ->
                    OutputFileActionsCard(
                        title = "Merged PDF Created",
                        file = outItem,
                        onPreview = { onOpenViewer(outItem) },
                        onSaveToDevice = { onRequestExport(outItem) },
                        onShare = { viewModel.shareFile(outItem) },
                        onOpenExternal = { viewModel.openExternal(outItem) }
                    )
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select PDFs to Merge",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.PDF),
            mimeFilter = arrayOf("application/pdf"),
            allowMultiple = true,
            onImportUris = { uris -> viewModel.importUris(uris, "PDFs") },
            onFilesPicked = { picked -> selectedPdfs = selectedPdfs + picked },
            onDismiss = { showPicker = false }
        )
    }
}

private enum class SplitMode(val label: String, val hint: String) {
    CUSTOM_RANGES("Custom Ranges", "e.g. 1-3, 5, 7-9 creates one PDF per range"),
    EXTRACT_SELECTED("Extract Selected Pages", "Combine tapped page thumbnails into one PDF"),
    EVERY_PAGE("Split Every Page", "Save every page as an individual PDF file")
}

@Composable
fun PdfSplitScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf<FileItem?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var splitMode by remember { mutableStateOf(SplitMode.CUSTOM_RANGES) }
    var rangesInput by remember { mutableStateOf("1-2") }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<List<FileItem>>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selectedPdf) {
        val pdf = selectedPdf
        pageCount = if (pdf != null) viewModel.pdfEngine.getPageCount(File(pdf.path)) else 0
        selectedPages = emptySet()
        if (pageCount > 0) {
            rangesInput = if (pageCount > 1) "1-${minOf(2, pageCount)}" else "1"
        }
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Split & Extract PDF",
                subtitle = "Split by range, selection, or every page",
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
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedPdf?.let { "${it.name} ($pageCount pages)" } ?: "Select Source PDF")
                }
            }

            if (selectedPdf == null) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Choose a PDF to Split",
                        description = "Preview page thumbnails, extract specific pages, or split by custom ranges.",
                        actionLabel = "Select PDF",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                val pdf = selectedPdf!!

                // Page Thumbnails Strip
                item {
                    Text("Page Previews ($pageCount pages)", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items((0 until pageCount).toList()) { idx ->
                            val isSel = idx in selectedPages
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(86.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (splitMode == SplitMode.EXTRACT_SELECTED) {
                                            selectedPages = if (isSel) selectedPages - idx else selectedPages + idx
                                        }
                                    }
                                    .border(
                                        width = if (isSel) 2.dp else 1.dp,
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(6.dp)
                            ) {
                                PdfThumbnailView(
                                    file = File(pdf.path),
                                    pageIndex = idx,
                                    pdfEngine = viewModel.pdfEngine,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.72f)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = if (isSel) "✓ P.${idx + 1}" else "Page ${idx + 1}",
                                    style = MonoMetricsStyle,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Split Modes
                item {
                    SplitMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { splitMode = mode }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = splitMode == mode, onClick = { splitMode = mode })
                            Column {
                                Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                Text(mode.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (splitMode == SplitMode.CUSTOM_RANGES) {
                    item {
                        OutlinedTextField(
                            value = rangesInput,
                            onValueChange = { rangesInput = it },
                            label = { Text("Page Ranges (1-$pageCount)") },
                            supportingText = { Text("Example: 1-3, 5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    Button(
                        onClick = {
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Splitting PDF...")
                                val base = File(pdf.path).nameWithoutExtension
                                val folder = viewModel.workspaceRepo.getTargetFolder(prefs.defaultOutputFolder)
                                val res: Result<List<File>> = when (splitMode) {
                                    SplitMode.CUSTOM_RANGES -> viewModel.pdfEngine.splitPdfByRanges(
                                        File(pdf.path), rangesInput, folder, base
                                    ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }

                                    SplitMode.EVERY_PAGE -> viewModel.pdfEngine.splitEveryPage(
                                        File(pdf.path), folder, base
                                    ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }

                                    SplitMode.EXTRACT_SELECTED -> {
                                        val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(
                                            prefs.defaultOutputFolder,
                                            "${base}_extracted.pdf"
                                        )
                                        viewModel.pdfEngine.extractPages(
                                            File(pdf.path),
                                            selectedPages.sorted(),
                                            outFile
                                        ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }
                                            .map { listOf(it) }
                                    }
                                }

                                res.onSuccess { files ->
                                    val items = files.map { viewModel.workspaceRepo.recordProcessedFile(it, "Split PDF") }
                                    status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Created ${items.size} PDF(s)", result = items)
                                }.onFailure { err ->
                                    if (err is kotlinx.coroutines.CancellationException) {
                                        status = ToolStatusState(ProcessStep.CANCELLED)
                                    } else {
                                        status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                    }
                                }
                            }
                        },
                        enabled = pageCount > 0 && status.step != ProcessStep.PROCESSING &&
                            (splitMode != SplitMode.EXTRACT_SELECTED || selectedPages.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (splitMode) {
                                SplitMode.CUSTOM_RANGES -> "Split by Ranges"
                                SplitMode.EXTRACT_SELECTED -> "Extract ${selectedPages.size} Selected Page(s)"
                                SplitMode.EVERY_PAGE -> "Split All $pageCount Pages"
                            }
                        )
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onCancel = { activeJob?.cancel() },
                    onDismissError = { status = ToolStatusState() }
                ) { items ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEach { outItem ->
                            OutputFileActionsCard(
                                title = "Saved ${outItem.name}",
                                file = outItem,
                                onPreview = { onOpenViewer(outItem) },
                                onSaveToDevice = { onRequestExport(outItem) },
                                onShare = { viewModel.shareFile(outItem) },
                                onOpenExternal = { viewModel.openExternal(outItem) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select PDF to Split",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.PDF),
            mimeFilter = arrayOf("application/pdf"),
            allowMultiple = false,
            onImportUris = { uris -> viewModel.importUris(uris, "PDFs") },
            onFilesPicked = { picked -> selectedPdf = picked.firstOrNull() },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
fun PdfCompressScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf<FileItem?>(null) }
    var level by remember(prefs.defaultPdfQuality) { mutableStateOf(prefs.defaultPdfQuality) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<CompressionReport>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Compress PDF",
                subtitle = "Optimize embedded images & preserve readability",
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
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedPdf?.let { "${it.name} (${formatFileSize(it.sizeBytes)})" } ?: "Select PDF to Compress")
                }
            }

            if (selectedPdf == null) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Choose a PDF Document",
                        description = "Reduce PDF file size by optimizing embedded images while maintaining crisp text.",
                        actionLabel = "Select PDF",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                val pdf = selectedPdf!!

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PdfThumbnailView(
                                file = File(pdf.path),
                                pageIndex = 0,
                                pdfEngine = viewModel.pdfEngine,
                                modifier = Modifier.size(56.dp, 74.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(pdf.name, style = MaterialTheme.typography.titleSmall)
                                Text("Original Size: ${formatFileSize(pdf.sizeBytes)}", style = MonoMetricsStyle)
                            }
                        }
                    }
                }

                item {
                    Text("Compression Level", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    CompressionLevel.entries.forEach { option ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { level = option },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                width = if (level == option) 1.5.dp else 1.dp,
                                color = if (level == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (level == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = level == option, onClick = { level = option })
                                Column {
                                    Text("${option.label} Compression", style = MaterialTheme.typography.titleSmall)
                                    Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Inspecting embedded images...")
                                val base = File(pdf.path).nameWithoutExtension
                                val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(
                                    prefs.defaultOutputFolder,
                                    "${base}_compressed.pdf"
                                )
                                val res = viewModel.pdfEngine.compressPdf(
                                    inputFile = File(pdf.path),
                                    outputFile = outFile,
                                    level = level
                                ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }

                                res.onSuccess { stats ->
                                    val savedItem = viewModel.workspaceRepo.recordProcessedFile(
                                        outputFile = stats.outputFile,
                                        processTag = if (stats.alreadyOptimal) "PDF Verified" else "Compressed PDF",
                                        originalSizeBytes = stats.originalBytes
                                    )
                                    status = ToolStatusState(
                                        ProcessStep.SUCCESS,
                                        1f,
                                        "Done",
                                        result = CompressionReport(
                                            outputFile = savedItem,
                                            originalBytes = stats.originalBytes,
                                            processedBytes = stats.processedBytes,
                                            alreadyOptimal = stats.alreadyOptimal
                                        )
                                    )
                                }.onFailure { err ->
                                    if (err is kotlinx.coroutines.CancellationException) {
                                        outFile.delete()
                                        status = ToolStatusState(ProcessStep.CANCELLED)
                                    } else {
                                        status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                    }
                                }
                            }
                        },
                        enabled = status.step != ProcessStep.PROCESSING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Compress PDF")
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onCancel = { activeJob?.cancel() },
                    onDismissError = { status = ToolStatusState() }
                ) { report ->
                    val summaryText = if (report.alreadyOptimal) {
                        "Original: ${formatFileSize(report.originalBytes)} • Output: ${formatFileSize(report.processedBytes)} (Already optimal — no size reduction possible)"
                    } else {
                        "Original: ${formatFileSize(report.originalBytes)} → Processed: ${formatFileSize(report.processedBytes)} (Saved ${report.reductionPercent}%)"
                    }
                    OutputFileActionsCard(
                        title = if (report.alreadyOptimal) "Document Already Optimal" else "Reduced by ${report.reductionPercent}%",
                        file = report.outputFile,
                        subtitle = summaryText,
                        onPreview = { onOpenViewer(report.outputFile) },
                        onSaveToDevice = { onRequestExport(report.outputFile) },
                        onShare = { viewModel.shareFile(report.outputFile) },
                        onOpenExternal = { viewModel.openExternal(report.outputFile) }
                    )
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select PDF to Compress",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.PDF),
            mimeFilter = arrayOf("application/pdf"),
            allowMultiple = false,
            onImportUris = { uris -> viewModel.importUris(uris, "PDFs") },
            onFilesPicked = { picked -> selectedPdf = picked.firstOrNull() },
            onDismiss = { showPicker = false }
        )
    }
}
