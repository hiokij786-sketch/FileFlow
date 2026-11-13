package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfViewerScreen(
    filePath: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenInfo: (String) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val file = remember(filePath) { File(filePath) }
    val fileItem = remember(filePath) {
        FileItem(
            id = file.absolutePath,
            name = file.name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
            mimeType = "application/pdf",
            category = FileCategory.PDF,
            folderName = file.parentFile?.name ?: "PDFs"
        )
    }

    var pageCount by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var darkReadingMode by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pageTexts by remember { mutableStateOf<List<String>?>(null) }
    var isExtractingText by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val currentPageIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val matchingPageIndices = remember(searchQuery, pageTexts) {
        val texts = pageTexts ?: return@remember emptyList()
        val q = searchQuery.trim()
        if (q.isEmpty()) emptyList()
        else texts.mapIndexedNotNull { idx, content ->
            if (content.contains(q, ignoreCase = true)) idx else null
        }
    }
    var activeMatchCursor by remember { mutableIntStateOf(0) }

    LaunchedEffect(filePath) {
        if (!file.exists()) {
            loadError = "File no longer exists on disk."
            return@LaunchedEffect
        }
        viewModel.markAccessed(fileItem)
        val count = viewModel.pdfEngine.getPageCount(file)
        if (count <= 0) {
            loadError = "Unable to open PDF. It may be corrupted or password-protected."
        } else {
            pageCount = count
        }
    }

    val invertColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = file.name,
                subtitle = if (pageCount > 0) "Page ${currentPageIndex + 1} of $pageCount" else "PDF Viewer",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        showSearchBar = !showSearchBar
                        if (showSearchBar && pageTexts == null && !isExtractingText) {
                            isExtractingText = true
                            scope.launch {
                                viewModel.pdfEngine.extractTextPerPage(file).onSuccess {
                                    pageTexts = it
                                }
                                isExtractingText = false
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search text")
                    }
                    IconButton(onClick = { darkReadingMode = !darkReadingMode }) {
                        Icon(
                            imageVector = if (darkReadingMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle dark reading mode"
                        )
                    }
                    IconButton(onClick = { onOpenInfo(file.absolutePath) }) {
                        Icon(Icons.Filled.Info, contentDescription = "PDF information")
                    }
                }
            )
        },
        bottomBar = {
            if (pageCount > 0) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f); if (zoomScale == 1f) panOffset = Offset.Zero }) {
                                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                            }
                            Text("${(zoomScale * 100).toInt()}%", style = MonoMetricsStyle)
                            IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f) }) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                            }
                        }

                        TextButton(onClick = {
                            jumpInput = "${currentPageIndex + 1}"
                            showJumpDialog = true
                        }) {
                            Text("${currentPageIndex + 1} / $pageCount", style = MonoMetricsStyle)
                        }

                        Row {
                            IconButton(onClick = { onRequestExport(fileItem) }) {
                                Icon(Icons.Filled.Download, contentDescription = "Save to device")
                            }
                            IconButton(onClick = { viewModel.shareFile(fileItem) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                            }
                            IconButton(onClick = { viewModel.openExternal(fileItem) }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open externally")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (darkReadingMode) Color(0xFF101216) else MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    activeMatchCursor = 0
                                },
                                placeholder = { Text("Search text inside PDF...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (matchingPageIndices.isNotEmpty()) {
                                IconButton(onClick = {
                                    activeMatchCursor = if (activeMatchCursor > 0) activeMatchCursor - 1 else matchingPageIndices.lastIndex
                                    scope.launch { listState.animateScrollToItem(matchingPageIndices[activeMatchCursor]) }
                                }) {
                                    Icon(Icons.Filled.NavigateBefore, contentDescription = "Previous match")
                                }
                                IconButton(onClick = {
                                    activeMatchCursor = (activeMatchCursor + 1) % matchingPageIndices.size
                                    scope.launch { listState.animateScrollToItem(matchingPageIndices[activeMatchCursor]) }
                                }) {
                                    Icon(Icons.Filled.NavigateNext, contentDescription = "Next match")
                                }
                            }
                            IconButton(onClick = { showSearchBar = false; searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        }
                        if (isExtractingText) {
                            Text("Indexing document text...", style = MaterialTheme.typography.bodySmall)
                        } else if (searchQuery.isNotBlank()) {
                            val totalChars = pageTexts?.sumOf { it.trim().length } ?: 0
                            val msg = when {
                                totalChars == 0 -> "No selectable text found (scanned image PDF)."
                                matchingPageIndices.isEmpty() -> "No matches found."
                                else -> "Found on ${matchingPageIndices.size} page(s) — showing P.${matchingPageIndices[activeMatchCursor] + 1}"
                            }
                            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (loadError != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = loadError!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (pageCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.05f) {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        zoomScale = 2f
                                    }
                                }
                            )
                        }
                        .pointerInput(zoomScale) {
                            if (zoomScale > 1f) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                                    panOffset = if (zoomScale == 1f) Offset.Zero else panOffset + pan
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = panOffset.x
                                translationY = panOffset.y
                            },
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(count = pageCount, key = { it }) { pageIdx ->
                            ViewerPdfPageItem(
                                file = file,
                                pageIndex = pageIdx,
                                isSearchHighlight = pageIdx in matchingPageIndices,
                                colorFilter = if (darkReadingMode) invertColorFilter else null,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Page") },
            text = {
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { jumpInput = it },
                    label = { Text("Page (1 - $pageCount)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val target = jumpInput.toIntOrNull()
                    if (target != null && target in 1..pageCount) {
                        scope.launch { listState.scrollToItem(target - 1) }
                    }
                    showJumpDialog = false
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ViewerPdfPageItem(
    file: File,
    pageIndex: Int,
    isSearchHighlight: Boolean,
    colorFilter: ColorFilter?,
    viewModel: MainViewModel
) {
    var bitmap by remember(file.absolutePath, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.absolutePath, pageIndex) {
        bitmap = viewModel.pdfEngine.renderPageBitmap(file, pageIndex, 1080)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        border = if (isSearchHighlight) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}
