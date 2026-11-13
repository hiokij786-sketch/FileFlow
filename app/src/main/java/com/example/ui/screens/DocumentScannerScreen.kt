package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.FileItem
import com.example.data.model.ImageFitMode
import com.example.data.model.PdfPageSizeOption
import com.example.data.model.ProcessStep
import com.example.data.model.ScanFilterMode
import com.example.data.model.ToolStatusState
import com.example.engine.ImageEngine
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.theme.GoldPrimary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot

private enum class ScannerStage {
    CAPTURE,
    PERSPECTIVE_AND_FILTER,
    DECK_EXPORT
}

@Composable
fun DocumentScannerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Request camera permission ONLY when Document Scanner opens
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var stage by remember { mutableStateOf(ScannerStage.CAPTURE) }
    var rawBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember {
        mutableStateOf(
            listOf(
                Offset(0.1f, 0.1f),
                Offset(0.9f, 0.1f),
                Offset(0.9f, 0.9f),
                Offset(0.1f, 0.9f)
            )
        )
    }
    var filterMode by remember { mutableStateOf(ScanFilterMode.ENHANCED) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1.1f) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var scannedPageFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var outputPdfName by remember { mutableStateOf("Scan_${System.currentTimeMillis() % 10000}.pdf") }
    var exportStatus by remember { mutableStateOf(ToolStatusState<FileItem>()) }

    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    val galleryImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                viewModel.workspaceRepo.copyUriToTemp(uri, "jpg").onSuccess { temp ->
                    val bmp = withContext(Dispatchers.IO) {
                        ImageEngine.decodeSafeBitmap(temp, 1600)?.let {
                            ImageEngine.applyExifOrientation(temp, it)
                        }
                    }
                    if (bmp != null) {
                        rawBitmap = bmp
                        corners = ImageEngine.detectDocumentCornersHeuristic(bmp)
                        rotationDegrees = 0
                        stage = ScannerStage.PERSPECTIVE_AND_FILTER
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "Document Scanner",
                subtitle = "${scannedPageFiles.size} scanned page(s)",
                onBack = onBack,
                actions = {
                    if (scannedPageFiles.isNotEmpty() && stage != ScannerStage.DECK_EXPORT) {
                        Button(onClick = { stage = ScannerStage.DECK_EXPORT }) {
                            Text("Review (${scannedPageFiles.size})")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (stage) {
            ScannerStage.CAPTURE -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hasCameraPermission) {
                            val previewView = remember { PreviewView(context) }
                            DisposableEffect(lifecycleOwner) {
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                                val executor = ContextCompat.getMainExecutor(context)
                                cameraProviderFuture.addListener({
                                    runCatching {
                                        val provider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        provider.unbindAll()
                                        provider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageCapture
                                        )
                                    }
                                }, executor)
                                onDispose {
                                    runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
                                }
                            }
                            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                            // Viewfinder alignment guide
                            Canvas(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                                drawRect(
                                    color = GoldPrimary.copy(alpha = 0.7f),
                                    style = Stroke(width = 3f)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Text(
                                    text = "Camera permission is required for live capture, or import an existing photo below.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Spacer(Modifier.height(14.dp))
                                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                                    Text("Grant Camera Permission")
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = {
                                galleryImportLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Import")
                            }

                            FloatingActionButton(
                                onClick = {
                                    if (!hasCameraPermission) {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                        return@FloatingActionButton
                                    }
                                    val tempPhoto = viewModel.workspaceRepo.createTempFile("scan_cap", "jpg")
                                    val outputOpts = ImageCapture.OutputFileOptions.Builder(tempPhoto).build()
                                    imageCapture.takePicture(
                                        outputOpts,
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageSavedCallback {
                                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                scope.launch {
                                                    val bmp = withContext(Dispatchers.IO) {
                                                        ImageEngine.decodeSafeBitmap(tempPhoto, 1600)?.let {
                                                            ImageEngine.applyExifOrientation(tempPhoto, it)
                                                        }
                                                    }
                                                    if (bmp != null) {
                                                        rawBitmap = bmp
                                                        corners = ImageEngine.detectDocumentCornersHeuristic(bmp)
                                                        rotationDegrees = 0
                                                        stage = ScannerStage.PERSPECTIVE_AND_FILTER
                                                    }
                                                }
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    )
                                },
                                shape = CircleShape,
                                containerColor = GoldPrimary,
                                contentColor = Color.White
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Capture Document")
                            }

                            OutlinedButton(
                                enabled = scannedPageFiles.isNotEmpty(),
                                onClick = { stage = ScannerStage.DECK_EXPORT }
                            ) {
                                Text("Pages (${scannedPageFiles.size})")
                            }
                        }
                    }
                }
            }

            ScannerStage.PERSPECTIVE_AND_FILTER -> {
                val src = rawBitmap
                var activeCornerIdx by remember { mutableIntStateOf(-1) }
                var previewEnhanced by remember { mutableStateOf<Bitmap?>(null) }

                LaunchedEffect(src, corners, filterMode, brightness, contrast, rotationDegrees) {
                    if (src != null) {
                        previewEnhanced = withContext(Dispatchers.IO) {
                            val warped = ImageEngine.warpPerspective(src, corners)
                            val enhanced = ImageEngine.applyScannerEnhancement(
                                warped,
                                filterMode,
                                brightness,
                                contrast,
                                rotationDegrees
                            )
                            warped.recycle()
                            enhanced
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (src != null) {
                        item {
                            Text("Drag gold corners on source to correct perspective:", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(6.dp))
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black)
                            ) {
                                val boxW = constraints.maxWidth.toFloat()
                                val boxH = constraints.maxHeight.toFloat()
                                Image(
                                    bitmap = src.asImageBitmap(),
                                    contentDescription = "Captured document corners",
                                    contentScale = ContentScale.FillBounds,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { startOffset ->
                                                    val norm = Offset(startOffset.x / boxW, startOffset.y / boxH)
                                                    activeCornerIdx = corners.indices.minByOrNull { idx ->
                                                        hypot(corners[idx].x - norm.x, corners[idx].y - norm.y)
                                                    } ?: -1
                                                },
                                                onDragEnd = { activeCornerIdx = -1 },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    if (activeCornerIdx in 0..3) {
                                                        val updated = corners.toMutableList()
                                                        val cur = updated[activeCornerIdx]
                                                        updated[activeCornerIdx] = Offset(
                                                            (cur.x + dragAmount.x / boxW).coerceIn(0.02f, 0.98f),
                                                            (cur.y + dragAmount.y / boxH).coerceIn(0.02f, 0.98f)
                                                        )
                                                        corners = updated
                                                    }
                                                }
                                            )
                                        }
                                )
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val pts = corners.map { Offset(it.x * size.width, it.y * size.height) }
                                    val poly = Path().apply {
                                        moveTo(pts[0].x, pts[0].y)
                                        lineTo(pts[1].x, pts[1].y)
                                        lineTo(pts[2].x, pts[2].y)
                                        lineTo(pts[3].x, pts[3].y)
                                        close()
                                    }
                                    drawPath(poly, color = GoldPrimary, style = Stroke(width = 4f))
                                    pts.forEach { pt ->
                                        drawCircle(color = GoldPrimary, radius = 16f, center = pt)
                                        drawCircle(color = Color.White, radius = 7f, center = pt)
                                    }
                                }
                            }
                        }

                        previewEnhanced?.let { enhancedBmp ->
                            item {
                                Text("Corrected Output Preview", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Image(
                                    bitmap = enhancedBmp.asImageBitmap(),
                                    contentDescription = "Enhanced scan output",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                )
                            }
                        }

                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(ScanFilterMode.entries) { _, m ->
                                    FilterChip(
                                        selected = filterMode == m,
                                        onClick = { filterMode = m },
                                        label = { Text(m.label) }
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Brightness", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
                                Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -50f..50f, modifier = Modifier.weight(1f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Contrast", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
                                Slider(value = contrast, onValueChange = { contrast = it }, valueRange = 0.7f..1.8f, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.RotateRight, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Rotate 90°")
                                }
                                Button(
                                    onClick = {
                                        val finalBmp = previewEnhanced ?: return@Button
                                        scope.launch {
                                            val pageFile = withContext(Dispatchers.IO) {
                                                val out = viewModel.workspaceRepo.createTempFile("scan_page", "jpg")
                                                FileOutputStream(out).use { fos ->
                                                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                                }
                                                out
                                            }
                                            scannedPageFiles = scannedPageFiles + pageFile
                                            stage = ScannerStage.DECK_EXPORT
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add Page")
                                }
                            }
                        }
                    }
                }
            }

            ScannerStage.DECK_EXPORT -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { stage = ScannerStage.CAPTURE },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Scan Another")
                            }
                            OutlinedButton(
                                onClick = {
                                    galleryImportLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Photo")
                            }
                        }
                    }

                    itemsIndexed(scannedPageFiles) { idx, pageFile ->
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
                                Text("Page ${idx + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    scannedPageFiles = scannedPageFiles.filterIndexed { i, _ -> i != idx }
                                    if (scannedPageFiles.isEmpty()) stage = ScannerStage.CAPTURE
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete page")
                                }
                            }
                        }
                    }

                    if (scannedPageFiles.isNotEmpty()) {
                        item {
                            OutlinedTextField(
                                value = outputPdfName,
                                onValueChange = { outputPdfName = it },
                                label = { Text("PDF Filename") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        exportStatus = ToolStatusState(ProcessStep.PROCESSING, 0.1f, "Building scanned PDF...")
                                        val safe = if (outputPdfName.endsWith(".pdf", true)) outputPdfName else "$outputPdfName.pdf"
                                        val outFile = viewModel.workspaceRepo.buildUniqueOutputFile("Scans", safe)
                                        viewModel.pdfEngine.imagesToPdf(
                                            imageFiles = scannedPageFiles,
                                            outputFile = outFile,
                                            pageSizeOption = PdfPageSizeOption.A4,
                                            customWidthPt = 595f,
                                            customHeightPt = 842f,
                                            landscape = false,
                                            marginPt = 16f,
                                            fitMode = ImageFitMode.FIT,
                                            jpegQuality = 90
                                        ) { p, m -> exportStatus = ToolStatusState(ProcessStep.PROCESSING, p, m) }
                                            .onSuccess { pdf ->
                                                val item = viewModel.workspaceRepo.recordProcessedFile(pdf, "Scanned PDF")
                                                exportStatus = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
                                            }
                                            .onFailure { err ->
                                                exportStatus = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                            }
                                    }
                                },
                                enabled = exportStatus.step != ProcessStep.PROCESSING,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Export ${scannedPageFiles.size}-Page PDF")
                            }
                        }
                    }

                    item {
                        ToolProgressAndResultCard(
                            state = exportStatus,
                            onDismissError = { exportStatus = ToolStatusState() }
                        ) { pdfItem ->
                            OutputFileActionsCard(
                                title = "Scanned PDF Saved",
                                file = pdfItem,
                                onPreview = { onOpenViewer(pdfItem) },
                                onSaveToDevice = { onRequestExport(pdfItem) },
                                onShare = { viewModel.shareFile(pdfItem) },
                                onOpenExternal = { viewModel.openExternal(pdfItem) }
                            )
                        }
                    }
                }
            }
        }
    }
}
