package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.ImageFormatOption
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.formatFileSize
import com.example.engine.ImageDimensions
import com.example.engine.ImageEngine
import com.example.engine.ImageProcessOptions
import com.example.engine.ImageProcessReport
import com.example.engine.NormalizedCropRect
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private enum class ImageToolMode(val label: String) {
    COMPRESS("Compress"),
    RESIZE("Resize"),
    CROP("Crop"),
    ROTATE("Rotate & Flip"),
    CONVERT("Convert"),
    BATCH("Batch")
}

private enum class CropPreset(val label: String, val rect: NormalizedCropRect) {
    FULL("Full (100%)", NormalizedCropRect(0f, 0f, 1f, 1f)),
    SQUARE("1:1 Square", NormalizedCropRect(0.12f, 0.12f, 0.88f, 0.88f)),
    CENTER_FOUR_THREE("4:3 Focus", NormalizedCropRect(0.08f, 0.14f, 0.92f, 0.86f)),
    WIDESCREEN("16:9 Wide", NormalizedCropRect(0.04f, 0.22f, 0.96f, 0.78f)),
    TRIM("Tight Trim", NormalizedCropRect(0.06f, 0.06f, 0.94f, 0.94f))
}

@Composable
fun ImageToolsHubScreen(
    onOpenStudio: (String) -> Unit,
    onOpenScanner: () -> Unit
) {
    val cards = listOf(
        Triple("compress", "Image Compressor", "Reduce photo file size with quality preview") to Icons.Filled.Compress,
        Triple("resize", "Image Resizer", "Custom dimensions with aspect ratio lock") to Icons.Filled.PhotoSizeSelectLarge,
        Triple("crop", "Image Cropper", "Interactive crop frame & aspect presets") to Icons.Filled.Crop,
        Triple("rotate", "Rotate & Flip", "90° rotation & horizontal mirroring") to Icons.Filled.RotateRight,
        Triple("convert", "Image Format Converter", "Convert JPG, PNG & WEBP seamlessly") to Icons.Filled.SwapHoriz,
        Triple("batch", "Batch Image Processor", "Optimize & convert multiple images at once") to Icons.Filled.Image
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Image Toolkit", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Optimize, resize, crop, convert, or scan documents with zero cloud uploads.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Featured Scanner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenScanner),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DocumentScanner,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Document Scanner", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondary)
                        Text(
                            "Camera capture, 4-point perspective deskew, filters & PDF export",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.78f)
                        )
                    }
                }
            }
        }

        items(cards) { (meta, icon) ->
            val (key, title, desc) = meta
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenStudio(key) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ImageToolkitScreen(
    initialTool: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var mode by remember(initialTool) {
        mutableStateOf(
            when (initialTool?.lowercase()) {
                "resize" -> ImageToolMode.RESIZE
                "crop" -> ImageToolMode.CROP
                "rotate" -> ImageToolMode.ROTATE
                "convert" -> ImageToolMode.CONVERT
                "batch" -> ImageToolMode.BATCH
                else -> ImageToolMode.COMPRESS
            }
        )
    }

    var selectedImages by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var dims by remember { mutableStateOf<ImageDimensions?>(null) }
    var previewBeforeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewAfterBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showAfterPreview by remember { mutableStateOf(false) }

    var outputFormat by remember(prefs.defaultImageFormat) { mutableStateOf(prefs.defaultImageFormat) }
    var quality by remember { mutableFloatStateOf(80f) }
    var widthInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var lockAspectRatio by remember { mutableStateOf(true) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(NormalizedCropRect()) }

    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<List<Pair<FileItem, ImageProcessReport>>>()) }
    var activeJob by remember { mutableStateOf<Job?>(null) }

    val primaryImage = selectedImages.firstOrNull()

    LaunchedEffect(primaryImage) {
        val item = primaryImage
        if (item != null) {
            val file = File(item.path)
            val d = ImageEngine.getImageDimensions(file)
            dims = d
            widthInput = d.width.toString()
            heightInput = d.height.toString()
            previewBeforeBitmap = ImageEngine.decodeSafeBitmap(file, 960)?.let {
                ImageEngine.applyExifOrientation(file, it)
            }
            previewAfterBitmap = null
            showAfterPreview = false
        } else {
            dims = null
            previewBeforeBitmap = null
            previewAfterBitmap = null
        }
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Image Studio",
                subtitle = mode.label,
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ImageToolMode.entries) { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m; status = ToolStatusState() },
                            label = { Text(m.label) }
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedImages.isEmpty()) {
                            if (mode == ImageToolMode.BATCH) "Select Multiple Images" else "Select Image"
                        } else if (selectedImages.size == 1) {
                            "${selectedImages.first().name} (${formatFileSize(selectedImages.first().sizeBytes)})"
                        } else {
                            "${selectedImages.size} images selected"
                        }
                    )
                }
            }

            if (selectedImages.isEmpty()) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.Image,
                        title = "Choose an Image to Edit",
                        description = "Compress, resize, crop, rotate, convert formats, or batch process images completely offline.",
                        actionLabel = "Pick Image(s)",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                // Before / After Preview Area
                previewBeforeBitmap?.let { beforeBmp ->
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showAfterPreview && previewAfterBitmap != null) "After Processing" else "Original Preview",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (previewAfterBitmap != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("After", style = MaterialTheme.typography.labelSmall)
                                            Spacer(Modifier.width(6.dp))
                                            Switch(checked = showAfterPreview, onCheckedChange = { showAfterPreview = it })
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                val shownBmp = if (showAfterPreview && previewAfterBitmap != null) previewAfterBitmap!! else beforeBmp
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.05f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = shownBmp.asImageBitmap(),
                                        contentDescription = "Image preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (mode == ImageToolMode.CROP && !showAfterPreview) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val leftPx = cropRect.left * size.width
                                            val topPx = cropRect.top * size.height
                                            val widthPx = (cropRect.right - cropRect.left) * size.width
                                            val heightPx = (cropRect.bottom - cropRect.top) * size.height
                                            drawRect(
                                                color = GoldPrimary,
                                                topLeft = Offset(leftPx, topPx),
                                                size = Size(widthPx, heightPx),
                                                style = Stroke(width = 4f)
                                            )
                                        }
                                    }
                                }
                                dims?.let { d ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Original: ${d.width} × ${d.height} px • ${formatFileSize(d.sizeBytes)}",
                                        style = MonoMetricsStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Mode-Specific Controls
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Output Format", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageFormatOption.entries.forEach { fmt ->
                                FilterChip(
                                    selected = outputFormat == fmt,
                                    onClick = { outputFormat = fmt },
                                    label = { Text(fmt.extension.uppercase()) }
                                )
                            }
                        }

                        if (outputFormat != ImageFormatOption.PNG) {
                            Text("Quality: ${quality.roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                            Slider(value = quality, onValueChange = { quality = it }, valueRange = 15f..100f)
                        }

                        if (mode == ImageToolMode.RESIZE || mode == ImageToolMode.BATCH) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Dimensions (px)", style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { lockAspectRatio = !lockAspectRatio }) {
                                    Icon(
                                        imageVector = if (lockAspectRatio) Icons.Filled.Link else Icons.Filled.LinkOff,
                                        contentDescription = "Toggle aspect ratio lock",
                                        tint = if (lockAspectRatio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = widthInput,
                                    onValueChange = { newW ->
                                        widthInput = newW
                                        val wVal = newW.toIntOrNull()
                                        val d = dims
                                        if (lockAspectRatio && wVal != null && d != null && d.width > 0) {
                                            heightInput = ((wVal.toFloat() / d.width) * d.height).roundToInt().toString()
                                        }
                                    },
                                    label = { Text("Width") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = heightInput,
                                    onValueChange = { newH ->
                                        heightInput = newH
                                        val hVal = newH.toIntOrNull()
                                        val d = dims
                                        if (lockAspectRatio && hVal != null && d != null && d.height > 0) {
                                            widthInput = ((hVal.toFloat() / d.height) * d.width).roundToInt().toString()
                                        }
                                    },
                                    label = { Text("Height") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            dims?.let { d ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0.5f to "50%", 0.75f to "75%", 1f to "100%").forEach { (factor, lbl) ->
                                        OutlinedButton(onClick = {
                                            widthInput = (d.width * factor).roundToInt().coerceAtLeast(16).toString()
                                            heightInput = (d.height * factor).roundToInt().coerceAtLeast(16).toString()
                                        }) { Text(lbl) }
                                    }
                                }
                            }
                        }

                        if (mode == ImageToolMode.CROP) {
                            Text("Crop Presets", style = MaterialTheme.typography.titleSmall)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(CropPreset.entries) { preset ->
                                    FilterChip(
                                        selected = cropRect == preset.rect,
                                        onClick = { cropRect = preset.rect },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                            Text("Horizontal Inset: ${(cropRect.left * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = cropRect.left,
                                onValueChange = { v ->
                                    val c = v.coerceIn(0f, 0.4f)
                                    cropRect = cropRect.copy(left = c, right = 1f - c)
                                },
                                valueRange = 0f..0.4f
                            )
                            Text("Vertical Inset: ${(cropRect.top * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = cropRect.top,
                                onValueChange = { v ->
                                    val c = v.coerceIn(0f, 0.4f)
                                    cropRect = cropRect.copy(top = c, bottom = 1f - c)
                                },
                                valueRange = 0f..0.4f
                            )
                        }

                        if (mode == ImageToolMode.ROTATE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.RotateRight, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rotate ($rotationDegrees°)")
                                }
                                OutlinedButton(
                                    onClick = { flipHorizontal = !flipHorizontal },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Flip, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (flipHorizontal) "Flipped" else "Flip H")
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
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.1f, "Processing image(s)...")
                                val opts = ImageProcessOptions(
                                    outputFormat = outputFormat,
                                    quality = quality.roundToInt(),
                                    targetWidth = widthInput.toIntOrNull(),
                                    targetHeight = heightInput.toIntOrNull(),
                                    rotationDegrees = rotationDegrees,
                                    flipHorizontal = flipHorizontal,
                                    cropRect = if (mode == ImageToolMode.CROP) cropRect else null
                                )
                                val outFolder = viewModel.workspaceRepo.getTargetFolder("Images")

                                if (selectedImages.size == 1 && mode != ImageToolMode.BATCH) {
                                    val src = File(selectedImages.first().path)
                                    val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(
                                        "Images",
                                        "${src.nameWithoutExtension}_${mode.name.lowercase()}.${outputFormat.extension}"
                                    )
                                    ImageEngine.processImage(src, outFile, opts)
                                        .onSuccess { rep ->
                                            val item = viewModel.workspaceRepo.recordProcessedFile(
                                                rep.outputFile,
                                                "Image ${mode.label}",
                                                rep.originalBytes
                                            )
                                            previewAfterBitmap = ImageEngine.decodeSafeBitmap(rep.outputFile, 960)
                                            showAfterPreview = true
                                            status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = listOf(item to rep))
                                        }
                                        .onFailure { err ->
                                            status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                        }
                                } else {
                                    ImageEngine.batchProcessImages(
                                        inputFiles = selectedImages.map { File(it.path) },
                                        outputDir = outFolder,
                                        options = opts
                                    ) { p, m -> status = ToolStatusState(ProcessStep.PROCESSING, p, m) }
                                        .onSuccess { reports ->
                                            val pairs = reports.map { rep ->
                                                val item = viewModel.workspaceRepo.recordProcessedFile(
                                                    rep.outputFile,
                                                    "Batch Image",
                                                    rep.originalBytes
                                                )
                                                item to rep
                                            }
                                            pairs.firstOrNull()?.second?.outputFile?.let {
                                                previewAfterBitmap = ImageEngine.decodeSafeBitmap(it, 960)
                                                showAfterPreview = true
                                            }
                                            status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = pairs)
                                        }
                                        .onFailure { err ->
                                            if (err is kotlinx.coroutines.CancellationException) {
                                                status = ToolStatusState(ProcessStep.CANCELLED)
                                            } else {
                                                status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                            }
                                        }
                                }
                            }
                        },
                        enabled = status.step != ProcessStep.PROCESSING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedImages.size > 1) "Process ${selectedImages.size} Images" else "Apply & Save Image")
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onCancel = { activeJob?.cancel() },
                    onDismissError = { status = ToolStatusState() }
                ) { pairs ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        pairs.forEach { (item, rep) ->
                            val diffPct = if (rep.originalBytes > 0) {
                                (((rep.originalBytes - rep.processedBytes).toDouble() / rep.originalBytes) * 100).roundToInt()
                            } else 0
                            val subtitle = "${formatFileSize(rep.originalBytes)} → ${formatFileSize(rep.processedBytes)} " +
                                "(${if (diffPct >= 0) "-$diffPct%" else "+${-diffPct}%"}) • ${rep.processedWidth}×${rep.processedHeight}"
                            OutputFileActionsCard(
                                title = "Processed ${item.name}",
                                file = item,
                                subtitle = subtitle,
                                onSaveToDevice = { onRequestExport(item) },
                                onShare = { viewModel.shareFile(item) },
                                onOpenExternal = { viewModel.openExternal(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select Image(s)",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.IMAGE),
            mimeFilter = arrayOf("image/*"),
            allowMultiple = mode == ImageToolMode.BATCH || selectedImages.size > 1,
            usePhotoPickerForImages = true,
            onImportUris = { uris -> viewModel.importUris(uris, "Images") },
            onFilesPicked = { picked ->
                selectedImages = picked
                status = ToolStatusState()
            },
            onDismiss = { showPicker = false }
        )
    }
}
