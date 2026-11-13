package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.formatFileSize
import com.example.engine.PdfEngine
import com.example.ui.theme.ArchiveAccent
import com.example.ui.theme.AudioAccent
import com.example.ui.theme.DocAccent
import com.example.ui.theme.EmeraldDarkTint
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.ImageAccent
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.theme.PdfAccent
import com.example.ui.theme.VideoAccent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Document + Flowing Gold Arrow Vector Logo for FILEFLOW.
 */
@Composable
fun FileFlowLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    darkBackdrop: Boolean = false
) {
    val surfaceColor = if (darkBackdrop) Color(0xFF14171F) else MaterialTheme.colorScheme.secondary
    val docColor = Color.White
    val goldColor = GoldPrimary

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.26f))
            .background(surfaceColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.7f)) {
            val w = this.size.width
            val h = this.size.height

            // Document silhouette with folded top-right corner
            val docPath = Path().apply {
                moveTo(w * 0.18f, h * 0.1f)
                lineTo(w * 0.62f, h * 0.1f)
                lineTo(w * 0.80f, h * 0.28f)
                lineTo(w * 0.80f, h * 0.90f)
                lineTo(w * 0.18f, h * 0.90f)
                close()
            }
            drawPath(path = docPath, color = docColor)

            // Folded corner triangle detail
            val foldPath = Path().apply {
                moveTo(w * 0.62f, h * 0.1f)
                lineTo(w * 0.62f, h * 0.28f)
                lineTo(w * 0.80f, h * 0.28f)
                close()
            }
            drawPath(path = foldPath, color = Color(0xFFD5D9E2))

            // Flowing gold arrow sweeping across the document
            val arrowPath = Path().apply {
                moveTo(w * 0.12f, h * 0.78f)
                cubicTo(
                    w * 0.34f, h * 0.56f,
                    w * 0.58f, h * 0.60f,
                    w * 0.86f, h * 0.26f
                )
            }
            drawPath(
                path = arrowPath,
                color = goldColor,
                style = Stroke(
                    width = w * 0.13f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            // Arrowhead
            val headPath = Path().apply {
                moveTo(w * 0.68f, h * 0.24f)
                lineTo(w * 0.90f, h * 0.22f)
                lineTo(w * 0.88f, h * 0.44f)
            }
            drawPath(
                path = headPath,
                color = goldColor,
                style = Stroke(
                    width = w * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileFlowToolTopBar(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("top_bar_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun categoryAccentColor(category: FileCategory): Color = when (category) {
    FileCategory.PDF -> PdfAccent
    FileCategory.IMAGE -> ImageAccent
    FileCategory.ARCHIVE -> ArchiveAccent
    FileCategory.DOCUMENT -> DocAccent
    FileCategory.AUDIO -> AudioAccent
    FileCategory.VIDEO -> VideoAccent
    else -> GoldPrimary
}

fun categoryIcon(category: FileCategory): ImageVector = when (category) {
    FileCategory.PDF -> Icons.Filled.PictureAsPdf
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.ARCHIVE -> Icons.Filled.FolderZip
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.AUDIO -> Icons.Filled.AudioFile
    FileCategory.VIDEO -> Icons.Filled.VideoFile
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
fun FileCategoryBadge(category: FileCategory, modifier: Modifier = Modifier) {
    val accent = categoryAccentColor(category)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIcon(category),
            contentDescription = category.label,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun FileListItemCard(
    item: FileItem,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onMoreClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(item.lastModified) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.lastModified))
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("file_item_${item.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.category == FileCategory.IMAGE && !item.isExternalSaf) {
                AsyncImage(
                    model = File(item.path),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            } else {
                FileCategoryBadge(category = item.category)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatFileSize(item.sizeBytes),
                        style = MonoMetricsStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!item.processTag.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.processTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (item.isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (item.isFavorite) GoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (onMoreClick != null) {
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "File options"
                    )
                }
            }
        }
    }
}

@Composable
fun PdfThumbnailView(
    file: File,
    pageIndex: Int,
    pdfEngine: PdfEngine,
    modifier: Modifier = Modifier,
    targetWidthPx: Int = 260
) {
    var bitmap by remember(file.absolutePath, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file.absolutePath, pageIndex) {
        bitmap = pdfEngine.renderPageBitmap(file, pageIndex, targetWidthPx)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "P.${pageIndex + 1}",
                style = MonoMetricsStyle,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun <T> ToolProgressAndResultCard(
    state: ToolStatusState<T>,
    onCancel: (() -> Unit)? = null,
    onDismissError: () -> Unit = {},
    successContent: @Composable (T) -> Unit
) {
    AnimatedVisibility(visible = state.step != ProcessStep.IDLE) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (state.step) {
                ProcessStep.PROCESSING -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = state.message.ifBlank { "Processing..." },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}%",
                                    style = MonoMetricsStyle,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { state.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                            if (onCancel != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = onCancel,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                ProcessStep.ERROR -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Operation could not complete",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = state.errorMessage ?: "Unexpected error occurred.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            IconButton(onClick = onDismissError) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Dismiss error",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                ProcessStep.CANCELLED -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Processing cancelled safely. Original files were not modified.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissError) { Text("OK") }
                        }
                    }
                }

                ProcessStep.SUCCESS -> {
                    state.result?.let { res ->
                        successContent(res)
                    }
                }

                ProcessStep.IDLE -> Unit
            }
        }
    }
}

@Composable
fun OutputFileActionsCard(
    title: String = "Ready to Use",
    file: FileItem,
    subtitle: String? = null,
    onPreview: (() -> Unit)? = null,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = EmeraldSuccess
                )
                Spacer(Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(text = file.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle ?: "${formatFileSize(file.sizeBytes)} • Saved to ${file.folderName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onPreview != null) {
                    Button(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Preview")
                    }
                }
                FilledTonalButton(
                    onClick = onSaveToDevice,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = onOpenExternal,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open")
                }
            }
        }
    }
}

/**
 * Dual-Source Picker Bottom Sheet: Pick from FILEFLOW Workspace OR Import from Device via SAF/PhotoPicker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceOrDeviceFilePickerSheet(
    title: String,
    workspaceFiles: List<FileItem>,
    allowedCategories: Set<FileCategory>,
    mimeFilter: Array<String>,
    allowMultiple: Boolean,
    usePhotoPickerForImages: Boolean = false,
    onImportUris: suspend (List<Uri>) -> List<FileItem>,
    onFilesPicked: (List<FileItem>) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(workspaceFiles, allowedCategories) {
        if (allowedCategories.isEmpty() || FileCategory.ALL in allowedCategories) workspaceFiles
        else workspaceFiles.filter { it.category in allowedCategories }
    }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingUris by remember { mutableStateOf<List<Uri>?>(null) }

    LaunchedEffect(pendingUris) {
        val uris = pendingUris ?: return@LaunchedEffect
        isImporting = true
        val imported = onImportUris(uris)
        isImporting = false
        pendingUris = null
        if (imported.isNotEmpty()) {
            onFilesPicked(imported)
            onDismiss()
        }
    }

    val openMultiDocs = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) pendingUris = uris
    }

    val openSingleDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingUris = listOf(uri)
    }

    val pickMultiPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) pendingUris = uris
    }

    val pickSinglePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingUris = listOf(uri)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Import directly from device storage or choose from your FILEFLOW library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    if (usePhotoPickerForImages) {
                        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        if (allowMultiple) pickMultiPhotos.launch(req) else pickSinglePhoto.launch(req)
                    } else {
                        if (allowMultiple) openMultiDocs.launch(mimeFilter) else openSingleDoc.launch(mimeFilter)
                    }
                },
                enabled = !isImporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isImporting) "Importing..." else "Browse Device Storage")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = "From FILEFLOW Library (${filtered.size})",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Text(
                    text = "No matching files in your FILEFLOW library yet. Tap Browse Device Storage above to import.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        val isSel = item.id in selectedIds
                        FileListItemCard(
                            item = item,
                            isSelected = isSel,
                            onClick = {
                                if (allowMultiple) {
                                    selectedIds = if (isSel) selectedIds - item.id else selectedIds + item.id
                                } else {
                                    onFilesPicked(listOf(item))
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }

            if (allowMultiple && selectedIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val picked = filtered.filter { it.id in selectedIds }
                        onFilesPicked(picked)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Use ${selectedIds.size} Selected File(s)")
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
