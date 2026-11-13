package com.example.data.model

import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class FileCategory(val label: String) {
    ALL("All"),
    PDF("PDFs"),
    IMAGE("Images"),
    VIDEO("Videos"),
    AUDIO("Audio"),
    DOCUMENT("Documents"),
    ARCHIVE("Archives"),
    OTHER("Other")
}

enum class SortField(val label: String) {
    DATE("Date"),
    NAME("Name"),
    SIZE("Size"),
    TYPE("Type")
}

enum class SortOrder(val label: String) {
    DESC("Descending"),
    ASC("Ascending")
}

enum class CompressionLevel(
    val label: String,
    val subtitle: String,
    val jpegQuality: Int,
    val maxImageDimension: Int
) {
    LOW("Low", "Best clarity, mild reduction", 85, 2200),
    MEDIUM("Medium", "Balanced readability & size", 65, 1500),
    HIGH("High", "Maximum reduction for sharing", 42, 1000)
}

enum class PdfPageSizeOption(val label: String, val widthPt: Float, val heightPt: Float) {
    A4("A4 (210 × 297 mm)", 595f, 842f),
    LETTER("US Letter (8.5 × 11 in)", 612f, 792f),
    CUSTOM("Custom Size", 595f, 842f)
}

enum class ImageFitMode(val label: String) {
    FIT("Fit Inside Margins"),
    FILL("Fill Page"),
    CENTER("Original Centered")
}

enum class ImageFormatOption(val extension: String, val mimeType: String, val label: String) {
    JPG("jpg", "image/jpeg", "JPEG (.jpg)"),
    PNG("png", "image/png", "PNG (.png)"),
    WEBP("webp", "image/webp", "WEBP (.webp)")
}

enum class ScanFilterMode(val label: String) {
    ORIGINAL("Original"),
    ENHANCED("Doc Enhance"),
    GRAYSCALE("Grayscale"),
    BLACK_WHITE("B&W Crisp")
}

enum class ProcessStep {
    IDLE,
    PROCESSING,
    SUCCESS,
    ERROR,
    CANCELLED
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val category: FileCategory,
    val folderName: String,
    val isFavorite: Boolean = false,
    val isProcessed: Boolean = false,
    val processTag: String? = null,
    val originalSizeBytes: Long? = null,
    val lastAccessedAt: Long = lastModified,
    val isExternalSaf: Boolean = false
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()
}

data class FolderInfo(
    val name: String,
    val path: String,
    val fileCount: Int,
    val totalBytes: Long,
    val isCustom: Boolean = false
)

data class StorageOverview(
    val deviceTotalBytes: Long,
    val deviceAvailableBytes: Long,
    val workspaceTotalBytes: Long,
    val processedFilesBytes: Long,
    val tempCacheBytes: Long,
    val categoryBreakdown: Map<FileCategory, Long>,
    val largeFiles: List<FileItem>
)

data class PdfMetadataInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val pdfVersion: String,
    val title: String?,
    val author: String?,
    val subject: String?,
    val creator: String?,
    val producer: String?,
    val creationDate: String?,
    val modificationDate: String?,
    val isEncrypted: Boolean
)

data class CompressionReport(
    val outputFile: FileItem,
    val originalBytes: Long,
    val processedBytes: Long,
    val alreadyOptimal: Boolean
) {
    val savedBytes: Long get() = (originalBytes - processedBytes).coerceAtLeast(0L)
    val reductionPercent: Int
        get() = if (originalBytes > 0 && processedBytes < originalBytes) {
            (((originalBytes - processedBytes).toDouble() / originalBytes.toDouble()) * 100.0).toInt()
        } else 0
}

data class ZipEntryInfo(
    val name: String,
    val sizeBytes: Long,
    val compressedSizeBytes: Long,
    val isDirectory: Boolean
)

data class ToolStatusState<T>(
    val step: ProcessStep = ProcessStep.IDLE,
    val progress: Float = 0f,
    val message: String = "",
    val result: T? = null,
    val errorMessage: String? = null
)

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups]
}

fun inferCategoryFromName(name: String, mimeType: String? = null): FileCategory {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext == "pdf" || mimeType == "application/pdf" -> FileCategory.PDF
        ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic") || mimeType?.startsWith("image/") == true -> FileCategory.IMAGE
        ext in setOf("mp4", "mkv", "mov", "avi", "webm") || mimeType?.startsWith("video/") == true -> FileCategory.VIDEO
        ext in setOf("mp3", "wav", "aac", "flac", "ogg", "m4a") || mimeType?.startsWith("audio/") == true -> FileCategory.AUDIO
        ext in setOf("txt", "md", "html", "htm", "csv", "json", "xml", "doc", "docx", "rtf") || mimeType?.startsWith("text/") == true -> FileCategory.DOCUMENT
        ext in setOf("zip", "rar", "7z", "tar", "gz") || mimeType == "application/zip" -> FileCategory.ARCHIVE
        else -> FileCategory.OTHER
    }
}

fun inferMimeType(name: String): String {
    return when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        "html", "htm" -> "text/html"
        "zip" -> "application/zip"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        else -> "application/octet-stream"
    }
}
