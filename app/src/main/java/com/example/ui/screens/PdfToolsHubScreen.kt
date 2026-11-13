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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private data class PdfToolEntry(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun PdfToolsHubScreen(
    viewModel: MainViewModel,
    onMerge: () -> Unit,
    onSplit: () -> Unit,
    onCompress: () -> Unit,
    onImagesToPdf: () -> Unit,
    onPdfToImages: () -> Unit,
    onOrganizer: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onProtect: () -> Unit,
    onInfo: () -> Unit,
    onScanner: () -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showViewerPicker by remember { mutableStateOf(false) }

    val tools = remember {
        listOf(
            PdfToolEntry("merge", "Merge PDFs", "Combine multiple PDFs in custom order", Icons.Filled.MergeType, onMerge),
            PdfToolEntry("split", "Split & Extract PDF", "Custom ranges, single pages, or extract", Icons.Filled.CallSplit, onSplit),
            PdfToolEntry("compress", "Compress PDF", "Low, medium, or high image optimization", Icons.Filled.Compress, onCompress),
            PdfToolEntry("img_to_pdf", "Images to PDF", "A4, Letter, custom size, margins & fit", Icons.Filled.PictureAsPdf, onImagesToPdf),
            PdfToolEntry("pdf_to_img", "PDF to Images", "Batch export pages as PNG or JPEG", Icons.Filled.Image, onPdfToImages),
            PdfToolEntry("organizer", "Page Organizer", "Reorder, rotate, delete, or extract pages", Icons.Filled.ViewQuilt, onOrganizer),
            PdfToolEntry("viewer", "PDF Viewer", "Zoom, text search & dark reading mode", Icons.AutoMirrored.Filled.ChromeReaderMode) { showViewerPicker = true },
            PdfToolEntry("protect", "Protect / Unlock PDF", "Password encryption & authorized unlock", Icons.Filled.Lock, onProtect),
            PdfToolEntry("info", "PDF Information", "Metadata, dates, encryption & version", Icons.Filled.Info, onInfo),
            PdfToolEntry("scanner", "Document Scanner", "Scan pages directly into PDF", Icons.Filled.DocumentScanner, onScanner)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("PDF Master Suite", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Complete on-device PDF processing powered by local engines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
        }

        items(tools, key = { it.id }) { tool ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = tool.onClick)
                    .testTag("pdf_tool_${tool.id}"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.title,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tool.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showViewerPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Open PDF in Viewer",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.PDF),
            mimeFilter = arrayOf("application/pdf"),
            allowMultiple = false,
            onImportUris = { uris -> viewModel.importUris(uris, "PDFs") },
            onFilesPicked = { picked ->
                picked.firstOrNull()?.let(onOpenViewer)
            },
            onDismiss = { showViewerPicker = false }
        )
    }
}
