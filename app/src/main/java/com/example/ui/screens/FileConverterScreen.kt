package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Html
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.ImageFormatOption
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.formatFileSize
import com.example.engine.ImageEngine
import com.example.engine.ImageProcessOptions
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private enum class ConverterMode(val label: String) {
    IMAGES_FORMAT("JPG / PNG / WEBP"),
    TXT_TO_PDF("TXT to PDF"),
    HTML_TO_PDF("HTML to PDF")
}

@Composable
fun FileConverterScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenImagesToPdf: () -> Unit,
    onOpenPdfToImages: () -> Unit,
    onOpenExtractZip: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(ConverterMode.IMAGES_FORMAT) }
    var selectedFile by remember { mutableStateOf<FileItem?>(null) }
    var targetImageFormat by remember { mutableStateOf(ImageFormatOption.PNG) }
    var textInput by remember { mutableStateOf("") }
    var htmlContent by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webViewReady by remember { mutableStateOf(false) }

    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<FileItem>()) }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "File Converter",
                subtitle = "Verified offline format conversions",
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
                Text("Quick Conversion Shortcuts", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onOpenImagesToPdf, modifier = Modifier.weight(1f)) {
                        Text("Images → PDF")
                    }
                    OutlinedButton(onClick = onOpenPdfToImages, modifier = Modifier.weight(1f)) {
                        Text("PDF → Images")
                    }
                    OutlinedButton(onClick = onOpenExtractZip, modifier = Modifier.weight(1f)) {
                        Text("ZIP Extract")
                    }
                }
            }

            item {
                Text("Direct Converters", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConverterMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = {
                                mode = m
                                selectedFile = null
                                htmlContent = null
                                webViewReady = false
                                status = ToolStatusState()
                            },
                            label = { Text(m.label) }
                        )
                    }
                }
            }

            when (mode) {
                ConverterMode.IMAGES_FORMAT -> {
                    item {
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedFile?.let { "${it.name} (${formatFileSize(it.sizeBytes)})" } ?: "Select JPG, PNG, or WEBP Image")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Convert To:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageFormatOption.entries.forEach { fmt ->
                                FilterChip(
                                    selected = targetImageFormat == fmt,
                                    onClick = { targetImageFormat = fmt },
                                    label = { Text(fmt.label) }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val src = selectedFile ?: return@Button
                                scope.launch {
                                    status = ToolStatusState(ProcessStep.PROCESSING, 0.3f, "Converting image...")
                                    val inFile = File(src.path)
                                    val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(
                                        "Converted",
                                        "${inFile.nameWithoutExtension}.${targetImageFormat.extension}"
                                    )
                                    ImageEngine.processImage(
                                        inFile,
                                        outFile,
                                        ImageProcessOptions(outputFormat = targetImageFormat, quality = 90)
                                    ).onSuccess { rep ->
                                        val item = viewModel.workspaceRepo.recordProcessedFile(
                                            rep.outputFile,
                                            "Converted to ${targetImageFormat.extension.uppercase()}",
                                            rep.originalBytes
                                        )
                                        status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
                                    }.onFailure { err ->
                                        status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                    }
                                }
                            },
                            enabled = selectedFile != null && status.step != ProcessStep.PROCESSING,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Convert Image")
                        }
                    }
                }

                ConverterMode.TXT_TO_PDF -> {
                    item {
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedFile?.name ?: "Load .TXT File (Optional)")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Text Content to Convert") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    status = ToolStatusState(ProcessStep.PROCESSING, 0.35f, "Formatting PDF pages...")
                                    val title = selectedFile?.name?.substringBeforeLast('.') ?: "TextDocument"
                                    val outFile = viewModel.workspaceRepo.buildUniqueOutputFile("Converted", "$title.pdf")
                                    viewModel.pdfEngine.textToPdf(textInput, outFile, title)
                                        .onSuccess { saved ->
                                            val item = viewModel.workspaceRepo.recordProcessedFile(saved, "TXT to PDF")
                                            status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
                                        }.onFailure { err ->
                                            status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                        }
                                }
                            },
                            enabled = textInput.isNotBlank() && status.step != ProcessStep.PROCESSING,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Convert Text to PDF")
                        }
                    }
                }

                ConverterMode.HTML_TO_PDF -> {
                    item {
                        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Html, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(selectedFile?.name ?: "Select .HTML / .HTM File")
                        }
                        htmlContent?.let { html ->
                            Spacer(Modifier.height(10.dp))
                            Text("HTML Preview", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            settings.javaScriptEnabled = false
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    webViewReady = true
                                                }
                                            }
                                            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                                            webViewRef = this
                                        }
                                    },
                                    update = { view ->
                                        webViewRef = view
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val wv = webViewRef ?: return@Button
                                    scope.launch {
                                        status = ToolStatusState(ProcessStep.PROCESSING, 0.4f, "Rendering HTML to PDF...")
                                        val title = selectedFile?.name?.substringBeforeLast('.') ?: "HtmlPage"
                                        val outFile = viewModel.workspaceRepo.buildUniqueOutputFile("Converted", "$title.pdf")
                                        val res = renderWebViewToPdf(wv, outFile)
                                        res.onSuccess { saved ->
                                            val item = viewModel.workspaceRepo.recordProcessedFile(saved, "HTML to PDF")
                                            status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
                                        }.onFailure { err ->
                                            status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                        }
                                    }
                                },
                                enabled = webViewReady && status.step != ProcessStep.PROCESSING,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Export HTML as PDF")
                            }
                        }
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onDismissError = { status = ToolStatusState() }
                ) { outItem ->
                    OutputFileActionsCard(
                        title = "Conversion Complete",
                        file = outItem,
                        onPreview = if (outItem.category == FileCategory.PDF) { { onOpenViewer(outItem) } } else null,
                        onSaveToDevice = { onRequestExport(outItem) },
                        onShare = { viewModel.shareFile(outItem) },
                        onOpenExternal = { viewModel.openExternal(outItem) }
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Supported Offline Conversions", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Supported locally: JPG ↔ PNG ↔ WEBP, Images → PDF, PDF → PNG/JPG, TXT → PDF, HTML → PDF, and ZIP extraction.\n\nUnsupported formats (DOCX, XLSX, PPTX, RAR, 7Z) require external proprietary codecs and are intentionally omitted to keep your files 100% private on-device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        val (cats, mimes) = when (mode) {
            ConverterMode.IMAGES_FORMAT -> setOf(FileCategory.IMAGE) to arrayOf("image/jpeg", "image/png", "image/webp")
            ConverterMode.TXT_TO_PDF -> setOf(FileCategory.DOCUMENT) to arrayOf("text/plain", "text/*")
            ConverterMode.HTML_TO_PDF -> setOf(FileCategory.DOCUMENT) to arrayOf("text/html", "application/xhtml+xml")
        }
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select Source File",
            workspaceFiles = allFiles,
            allowedCategories = cats,
            mimeFilter = mimes,
            allowMultiple = false,
            usePhotoPickerForImages = mode == ConverterMode.IMAGES_FORMAT,
            onImportUris = { uris -> viewModel.importUris(uris, "Imported") },
            onFilesPicked = { picked ->
                val item = picked.firstOrNull()
                selectedFile = item
                if (item != null) {
                    if (mode == ConverterMode.TXT_TO_PDF) {
                        textInput = runCatching { File(item.path).readText() }.getOrDefault("")
                    } else if (mode == ConverterMode.HTML_TO_PDF) {
                        htmlContent = runCatching { File(item.path).readText() }.getOrNull()
                    }
                }
            },
            onDismiss = { showPicker = false }
        )
    }
}

private suspend fun renderWebViewToPdf(webView: WebView, outFile: File): Result<File> {
    return runCatching {
        val bitmap = withContext(Dispatchers.Main) {
            val width = webView.width.coerceAtLeast(720)
            val contentH = (webView.contentHeight * webView.scale).toInt().coerceAtLeast(webView.height.coerceAtLeast(960))
            val bmp = Bitmap.createBitmap(width, contentH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(android.graphics.Color.WHITE)
            webView.draw(canvas)
            bmp
        }
        withContext(Dispatchers.IO) {
            val pageW = 595
            val pageH = 842
            val scale = pageW.toFloat() / bitmap.width.toFloat()
            val scaledH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, pageW, scaledH, true)
            bitmap.recycle()

            val pdf = PdfDocument()
            var yOffset = 0
            var pageNum = 1
            while (yOffset < scaled.height) {
                val sliceH = minOf(pageH, scaled.height - yOffset)
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                val page = pdf.startPage(pageInfo)
                val slice = Bitmap.createBitmap(scaled, 0, yOffset, pageW, sliceH)
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.drawBitmap(slice, 0f, 0f, null)
                slice.recycle()
                pdf.finishPage(page)
                yOffset += sliceH
                pageNum++
            }
            scaled.recycle()
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()
            outFile
        }
    }
}
