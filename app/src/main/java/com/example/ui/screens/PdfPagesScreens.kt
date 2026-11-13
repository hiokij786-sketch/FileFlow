package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageFormatOption
import com.example.data.model.PdfPageSizeOption
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.formatFileSize
import com.example.engine.PdfPageEditSpec
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.PdfThumbnailView
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ImagesToPdfScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedImages by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var pageSize by remember { mutableStateOf(PdfPageSizeOption.A4) }
    var customWidthMm by remember { mutableStateOf("210") }
    var customHeightMm by remember { mutableStateOf("297") }
    var landscape by remember { mutableStateOf(false) }
    var marginPt by remember { mutableFloatStateOf(24f) }
    var fitMode by remember { mutableStateOf(ImageFitMode.FIT) }
    var quality by remember { mutableFloatStateOf(88f) }
    var outputFileName by remember { mutableStateOf("Images_${System.currentTimeMillis() % 10000}.pdf") }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<FileItem>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Images to PDF",
                subtitle = "A4, Letter, custom size, margins & fit",
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
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Select Images (${selectedImages.size})")
                }
            }

            if (selectedImages.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.Image,
                        title = "Choose Images to Convert",
                        description = "Pick JPG, PNG, or WEBP images, arrange their page order, and customize layout & margins.",
                        actionLabel = "Select Images",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                itemsIndexed(selectedImages, key = { idx, item -> "${idx}_${item.id}" }) { index, item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = File(item.path),
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${index + 1}. ${item.name}", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(formatFileSize(item.sizeBytes), style = MonoMetricsStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val m = selectedImages.toMutableList()
                                    val it = m.removeAt(index)
                                    m.add(index - 1, it)
                                    selectedImages = m
                                }
                            ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                            IconButton(
                                enabled = index < selectedImages.lastIndex,
                                onClick = {
                                    val m = selectedImages.toMutableList()
                                    val it = m.removeAt(index)
                                    m.add(index + 1, it)
                                    selectedImages = m
                                }
                            ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                            IconButton(onClick = {
                                selectedImages = selectedImages.filterIndexed { i, _ -> i != index }
                            }) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
                        }
                    }
                }

                item {
                    Text("Page Size", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PdfPageSizeOption.entries) { opt ->
                            FilterChip(
                                selected = pageSize == opt,
                                onClick = { pageSize = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                }

                if (pageSize == PdfPageSizeOption.CUSTOM) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = customWidthMm,
                                onValueChange = { customWidthMm = it },
                                label = { Text("Width (mm)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customHeightMm,
                                onValueChange = { customHeightMm = it },
                                label = { Text("Height (mm)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Landscape Orientation", style = MaterialTheme.typography.titleSmall)
                            Text(if (landscape) "Landscape" else "Portrait", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = landscape, onCheckedChange = { landscape = it })
                    }
                }

                item {
                    Text("Fit Mode", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ImageFitMode.entries) { mode ->
                            FilterChip(
                                selected = fitMode == mode,
                                onClick = { fitMode = mode },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }

                item {
                    Text("Margins: ${marginPt.roundToInt()} pt", style = MaterialTheme.typography.titleSmall)
                    Slider(value = marginPt, onValueChange = { marginPt = it }, valueRange = 0f..72f)
                    Text("Image Quality: ${quality.roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 30f..100f)
                    OutlinedTextField(
                        value = outputFileName,
                        onValueChange = { outputFileName = it },
                        label = { Text("Output PDF Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Button(
                        onClick = {
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Creating PDF...")
                                val safeName = if (outputFileName.endsWith(".pdf", true)) outputFileName else "$outputFileName.pdf"
                                val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(prefs.defaultOutputFolder, safeName)
                                val mmToPt = 2.83465f
                                val cW = (customWidthMm.toFloatOrNull() ?: 210f) * mmToPt
                                val cH = (customHeightMm.toFloatOrNull() ?: 297f) * mmToPt
                                val res = viewModel.pdfEngine.imagesToPdf(
                                    imageFiles = selectedImages.map { File(it.path) },
                                    outputFile = outFile,
                                    pageSizeOption = pageSize,
                                    customWidthPt = cW,
                                    customHeightPt = cH,
                                    landscape = landscape,
                                    marginPt = marginPt,
                                    fitMode = fitMode,
                                    jpegQuality = quality.roundToInt()
                                ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }

                                res.onSuccess { pdf ->
                                    val saved = viewModel.workspaceRepo.recordProcessedFile(pdf, "Images to PDF")
                                    status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = saved)
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
                        Text("Create PDF (${selectedImages.size} Pages)")
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
                        title = "PDF Created",
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
            title = "Select Images for PDF",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.IMAGE),
            mimeFilter = arrayOf("image/*"),
            allowMultiple = true,
            usePhotoPickerForImages = true,
            onImportUris = { uris -> viewModel.importUris(uris, "Images") },
            onFilesPicked = { picked -> selectedImages = selectedImages + picked },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
fun PdfToImagesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf<FileItem?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var selectedPages by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var rangeText by remember { mutableStateOf("") }
    var useCustomRange by remember { mutableStateOf(false) }
    var format by remember(prefs.defaultImageFormat) {
        mutableStateOf(if (prefs.defaultImageFormat == ImageFormatOption.WEBP) ImageFormatOption.PNG else prefs.defaultImageFormat)
    }
    var dpi by remember { mutableIntStateOf(220) }
    var quality by remember { mutableFloatStateOf(90f) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<List<FileItem>>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selectedPdf) {
        val pdf = selectedPdf
        pageCount = if (pdf != null) viewModel.pdfEngine.getPageCount(File(pdf.path)) else 0
        selectedPages = (0 until pageCount).toSet()
        rangeText = if (pageCount > 0) "1-$pageCount" else ""
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "PDF to Images",
                subtitle = "Export PDF pages as PNG or JPEG",
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
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedPdf?.let { "${it.name} ($pageCount pages)" } ?: "Select Source PDF")
                }
            }

            if (selectedPdf == null) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Export PDF Pages as Images",
                        description = "Pick a PDF, choose individual pages or ranges, set DPI resolution, and batch export.",
                        actionLabel = "Select PDF",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                val pdf = selectedPdf!!
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Pages (${selectedPages.size}/$pageCount)", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !useCustomRange && selectedPages.size == pageCount,
                                onClick = {
                                    useCustomRange = false
                                    selectedPages = (0 until pageCount).toSet()
                                },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = useCustomRange,
                                onClick = { useCustomRange = !useCustomRange },
                                label = { Text("Range") }
                            )
                        }
                    }
                }

                if (useCustomRange) {
                    item {
                        OutlinedTextField(
                            value = rangeText,
                            onValueChange = { rangeText = it },
                            label = { Text("Page Range (e.g. 1-3, 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items((0 until pageCount).toList()) { idx ->
                                val isSel = idx in selectedPages
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(76.dp)
                                        .clickable {
                                            selectedPages = if (isSel) selectedPages - idx else selectedPages + idx
                                        }
                                ) {
                                    PdfThumbnailView(
                                        file = File(pdf.path),
                                        pageIndex = idx,
                                        pdfEngine = viewModel.pdfEngine,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(0.72f)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSel,
                                            onCheckedChange = { checked ->
                                                selectedPages = if (checked) selectedPages + idx else selectedPages - idx
                                            }
                                        )
                                        Text("${idx + 1}", style = MonoMetricsStyle)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Image Format", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ImageFormatOption.PNG, ImageFormatOption.JPG).forEach { opt ->
                            FilterChip(
                                selected = format == opt,
                                onClick = { format = opt },
                                label = { Text(opt.label) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Resolution", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(150 to "Standard (150 DPI)", 220 to "High (220 DPI)", 300 to "Print (300 DPI)").forEach { (valDpi, lbl) ->
                            FilterChip(
                                selected = dpi == valDpi,
                                onClick = { dpi = valDpi },
                                label = { Text(lbl) }
                            )
                        }
                    }
                    if (format == ImageFormatOption.JPG) {
                        Spacer(Modifier.height(6.dp))
                        Text("JPEG Quality: ${quality.roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                        Slider(value = quality, onValueChange = { quality = it }, valueRange = 40f..100f)
                    }
                }

                item {
                    Button(
                        onClick = {
                            activeJob?.cancel()
                            activeJob = scope.launch {
                                val indices = if (useCustomRange) {
                                    viewModel.pdfEngine.parseRangeGroups(rangeText, pageCount).flatten().distinct()
                                } else {
                                    selectedPages.sorted()
                                }
                                if (indices.isEmpty()) {
                                    status = ToolStatusState(ProcessStep.ERROR, errorMessage = "Select at least one valid page.")
                                    return@launch
                                }
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Rendering pages...")
                                val outFolder = viewModel.workspaceRepo.getTargetFolder("Images")
                                val base = File(pdf.path).nameWithoutExtension
                                val res = viewModel.pdfEngine.pdfToImages(
                                    inputFile = File(pdf.path),
                                    outputFolder = outFolder,
                                    baseName = base,
                                    pageIndicesZeroBased = indices,
                                    format = format,
                                    dpi = dpi,
                                    quality = quality.roundToInt()
                                ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }

                                res.onSuccess { files ->
                                    val saved = files.map { viewModel.workspaceRepo.recordProcessedFile(it, "PDF to Image") }
                                    status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Exported ${saved.size} image(s)", result = saved)
                                }.onFailure { err ->
                                    if (err is kotlinx.coroutines.CancellationException) {
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
                        Text("Export Images")
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onCancel = { activeJob?.cancel() },
                    onDismissError = { status = ToolStatusState() }
                ) { images ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        images.take(6).forEach { img ->
                            OutputFileActionsCard(
                                title = "Exported ${img.name}",
                                file = img,
                                onSaveToDevice = { onRequestExport(img) },
                                onShare = { viewModel.shareFile(img) },
                                onOpenExternal = { viewModel.openExternal(img) }
                            )
                        }
                        if (images.size > 6) {
                            Text(
                                text = "All ${images.size} images saved to your FILEFLOW Images folder.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select PDF to Export as Images",
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

private data class OrganizerPageItem(
    val uid: Int,
    val originalIndex: Int,
    val rotationDegrees: Int = 0,
    val selectedForExtract: Boolean = false
)

@Composable
fun PdfOrganizerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf<FileItem?>(null) }
    var pages by remember { mutableStateOf<List<OrganizerPageItem>>(emptyList()) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<FileItem>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(selectedPdf) {
        val pdf = selectedPdf
        if (pdf != null) {
            val count = viewModel.pdfEngine.getPageCount(File(pdf.path))
            pages = (0 until count).map { OrganizerPageItem(uid = it, originalIndex = it) }
        } else {
            pages = emptyList()
        }
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "PDF Page Organizer",
                subtitle = "Reorder, rotate, delete & extract pages",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedPdf?.let { "${it.name} (${pages.size} active pages)" } ?: "Select PDF to Organize")
                }
            }

            if (selectedPdf == null) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Organize PDF Pages",
                        description = "Reorder pages, rotate orientation, delete unwanted pages, or extract a subset into a new PDF.",
                        actionLabel = "Select PDF",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                val pdf = selectedPdf!!
                itemsIndexed(pages, key = { _, p -> p.uid }) { index, pageItem ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(
                            1.dp,
                            if (pageItem.selectedForExtract) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = pageItem.selectedForExtract,
                                onCheckedChange = { checked ->
                                    pages = pages.mapIndexed { i, item ->
                                        if (i == index) item.copy(selectedForExtract = checked) else item
                                    }
                                }
                            )
                            PdfThumbnailView(
                                file = File(pdf.path),
                                pageIndex = pageItem.originalIndex,
                                pdfEngine = viewModel.pdfEngine,
                                modifier = Modifier
                                    .size(48.dp, 64.dp)
                                    .rotate(pageItem.rotationDegrees.toFloat())
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Pos ${index + 1} (Orig P.${pageItem.originalIndex + 1})", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (pageItem.rotationDegrees == 0) "0° rotation" else "+${pageItem.rotationDegrees}° rotated",
                                    style = MonoMetricsStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = {
                                pages = pages.mapIndexed { i, item ->
                                    if (i == index) item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360) else item
                                }
                            }) {
                                Icon(Icons.Filled.RotateRight, contentDescription = "Rotate 90 degrees")
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = {
                                    val m = pages.toMutableList()
                                    val moved = m.removeAt(index)
                                    m.add(index - 1, moved)
                                    pages = m
                                }
                            ) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up") }
                            IconButton(
                                enabled = index < pages.lastIndex,
                                onClick = {
                                    val m = pages.toMutableList()
                                    val moved = m.removeAt(index)
                                    m.add(index + 1, moved)
                                    pages = m
                                }
                            ) { Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down") }
                            IconButton(
                                enabled = pages.size > 1,
                                onClick = {
                                    pages = pages.filterIndexed { i, _ -> i != index }
                                }
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete page") }
                        }
                    }
                }

                item {
                    val checkedPages = pages.filter { it.selectedForExtract }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (checkedPages.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    activeJob?.cancel()
                                    activeJob = scope.launch {
                                        status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Extracting pages...")
                                        val base = File(pdf.path).nameWithoutExtension
                                        val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(prefs.defaultOutputFolder, "${base}_extracted.pdf")
                                        val specs = checkedPages.map { PdfPageEditSpec(it.originalIndex, it.rotationDegrees) }
                                        viewModel.pdfEngine.organizePages(File(pdf.path), specs, outFile) { p, m ->
                                            status = ToolStatusState(ProcessStep.PROCESSING, p, m)
                                        }.onSuccess { savedFile ->
                                            val item = viewModel.workspaceRepo.recordProcessedFile(savedFile, "Extracted Pages")
                                            status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
                                        }.onFailure { err ->
                                            status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Extract (${checkedPages.size})")
                            }
                        }

                        Button(
                            onClick = {
                                activeJob?.cancel()
                                activeJob = scope.launch {
                                    status = ToolStatusState(ProcessStep.PROCESSING, 0.05f, "Saving organized PDF...")
                                    val base = File(pdf.path).nameWithoutExtension
                                    val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(prefs.defaultOutputFolder, "${base}_organized.pdf")
                                    val specs = pages.map { PdfPageEditSpec(it.originalIndex, it.rotationDegrees) }
                                    viewModel.pdfEngine.organizePages(File(pdf.path), specs, outFile) { p, m ->
                                        status = ToolStatusState(ProcessStep.PROCESSING, p, m)
                                    }.onSuccess { savedFile ->
                                        val item = viewModel.workspaceRepo.recordProcessedFile(savedFile, "Organized PDF")
                                        status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
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
                            enabled = pages.isNotEmpty() && status.step != ProcessStep.PROCESSING,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Modified PDF")
                        }
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
                        title = "Modified PDF Saved",
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
            title = "Select PDF to Organize",
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
