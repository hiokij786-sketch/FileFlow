package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageFormatOption
import com.example.data.model.PdfMetadataInfo
import com.example.data.model.PdfPageSizeOption
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfPageEditSpec(
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0
)

data class PdfCompressionStats(
    val outputFile: File,
    val originalBytes: Long,
    val processedBytes: Long,
    val alreadyOptimal: Boolean,
    val imagesOptimized: Int
)

class PdfEngine(private val context: Context) {

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    suspend fun getPdfInfo(file: File, password: String? = null): Result<PdfMetadataInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(file.exists() && file.length() > 0L) { "PDF file does not exist or is empty." }
            val doc = try {
                if (password.isNullOrEmpty()) PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
                else PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw IllegalStateException("This PDF is password-protected. Please enter the correct password.")
            }

            doc.use { document ->
                val info = document.documentInformation
                PdfMetadataInfo(
                    fileName = file.name,
                    fileSizeBytes = file.length(),
                    pageCount = document.numberOfPages,
                    pdfVersion = "PDF ${document.version}",
                    title = info?.title?.takeIf { it.isNotBlank() },
                    author = info?.author?.takeIf { it.isNotBlank() },
                    subject = info?.subject?.takeIf { it.isNotBlank() },
                    creator = info?.creator?.takeIf { it.isNotBlank() },
                    producer = info?.producer?.takeIf { it.isNotBlank() },
                    creationDate = info?.creationDate?.time?.let { dateFormatter.format(it) },
                    modificationDate = info?.modificationDate?.time?.let { dateFormatter.format(it) },
                    isEncrypted = document.isEncrypted
                )
            }
        }
    }

    suspend fun getPageCount(file: File): Int = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching {
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    suspend fun renderPageBitmap(
        file: File,
        pageIndex: Int,
        targetWidthPx: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
                    renderer.openPage(pageIndex).use { page ->
                        val ratio = page.height.toFloat() / max(1, page.width).toFloat()
                        val width = targetWidthPx.coerceIn(120, 2400)
                        val height = max(1, (width * ratio).roundToInt())
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    suspend fun extractTextPerPage(file: File): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val stripper = PDFTextStripper()
                val pages = mutableListOf<String>()
                for (i in 1..doc.numberOfPages) {
                    currentCoroutineContext().ensureActive()
                    stripper.startPage = i
                    stripper.endPage = i
                    pages.add(stripper.getText(doc))
                }
                pages
            }
        }
    }

    suspend fun mergePdfs(
        inputFiles: List<File>,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFiles.size >= 2) { "Select at least 2 PDF files to merge." }
            onProgress(0.05f, "Preparing ${inputFiles.size} documents...")
            val merger = PDFMergerUtility()
            merger.destinationFileName = outputFile.absolutePath
            inputFiles.forEachIndexed { idx, file ->
                currentCoroutineContext().ensureActive()
                require(file.exists()) { "Missing file: ${file.name}" }
                merger.addSource(file)
                onProgress(0.1f + 0.4f * ((idx + 1).toFloat() / inputFiles.size), "Queued ${file.name}")
            }
            onProgress(0.6f, "Merging PDF streams...")
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            onProgress(1f, "Merge complete")
            outputFile
        }
    }

    suspend fun splitPdfByRanges(
        inputFile: File,
        rangesText: String,
        outputFolder: File,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { sourceDoc ->
                val totalPages = sourceDoc.numberOfPages
                val parsedGroups = parseRangeGroups(rangesText, totalPages)
                require(parsedGroups.isNotEmpty()) {
                    "Enter valid page ranges such as 1-3, 5, 7-$totalPages."
                }
                val outputs = mutableListOf<File>()
                parsedGroups.forEachIndexed { groupIdx, pageIndices ->
                    currentCoroutineContext().ensureActive()
                    val label = if (pageIndices.size == 1) "p${pageIndices.first() + 1}"
                    else "p${pageIndices.first() + 1}-${pageIndices.last() + 1}"
                    val outFile = buildUniqueFile(outputFolder, "${baseName}_$label.pdf")
                    PDDocument().use { outDoc ->
                        pageIndices.forEach { pageIdx ->
                            outDoc.importPage(sourceDoc.getPage(pageIdx))
                        }
                        outDoc.save(outFile)
                    }
                    outputs.add(outFile)
                    onProgress((groupIdx + 1).toFloat() / parsedGroups.size, "Created ${outFile.name}")
                }
                outputs
            }
        }
    }

    suspend fun splitEveryPage(
        inputFile: File,
        outputFolder: File,
        baseName: String,
        onProgress: (Float, String) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { sourceDoc ->
                val total = sourceDoc.numberOfPages
                val outputs = mutableListOf<File>()
                for (i in 0 until total) {
                    currentCoroutineContext().ensureActive()
                    val outFile = buildUniqueFile(outputFolder, "${baseName}_page_${i + 1}.pdf")
                    PDDocument().use { singleDoc ->
                        singleDoc.importPage(sourceDoc.getPage(i))
                        singleDoc.save(outFile)
                    }
                    outputs.add(outFile)
                    onProgress((i + 1).toFloat() / total, "Extracted page ${i + 1} of $total")
                }
                outputs
            }
        }
    }

    suspend fun extractPages(
        inputFile: File,
        pageIndicesZeroBased: List<Int>,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(pageIndicesZeroBased.isNotEmpty()) { "Select at least one page to extract." }
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { sourceDoc ->
                PDDocument().use { outDoc ->
                    pageIndicesZeroBased.forEachIndexed { idx, pageIdx ->
                        currentCoroutineContext().ensureActive()
                        if (pageIdx in 0 until sourceDoc.numberOfPages) {
                            outDoc.importPage(sourceDoc.getPage(pageIdx))
                        }
                        onProgress((idx + 1).toFloat() / pageIndicesZeroBased.size, "Extracting page ${pageIdx + 1}")
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun compressPdf(
        inputFile: File,
        outputFile: File,
        level: CompressionLevel,
        onProgress: (Float, String) -> Unit
    ): Result<PdfCompressionStats> = withContext(Dispatchers.IO) {
        runCatching {
            val originalSize = inputFile.length()
            var optimizedImages = 0
            val tempCandidate = File(outputFile.parentFile, ".cmp_${System.currentTimeMillis()}.pdf")

            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val totalPages = max(1, doc.numberOfPages)
                for (i in 0 until doc.numberOfPages) {
                    currentCoroutineContext().ensureActive()
                    val page = doc.getPage(i)
                    val resources = page.resources
                    if (resources != null) {
                        val names = resources.xObjectNames.toList()
                        for (name in names) {
                            currentCoroutineContext().ensureActive()
                            val xObj = runCatching { resources.getXObject(name) }.getOrNull()
                            if (xObj is PDImageXObject) {
                                val srcBitmap = runCatching { xObj.image }.getOrNull()
                                if (srcBitmap != null) {
                                    val scaled = scaleBitmapDown(srcBitmap, level.maxImageDimension)
                                    val compressedObj = JPEGFactory.createFromImage(
                                        doc,
                                        scaled,
                                        level.jpegQuality / 100f
                                    )
                                    resources.put(name, compressedObj)
                                    if (scaled !== srcBitmap) scaled.recycle()
                                    srcBitmap.recycle()
                                    optimizedImages++
                                }
                            }
                        }
                    }
                    onProgress(0.1f + 0.75f * ((i + 1).toFloat() / totalPages), "Optimizing page ${i + 1} of $totalPages")
                }
                onProgress(0.9f, "Writing compressed PDF...")
                doc.save(tempCandidate)
            }

            val compressedSize = tempCandidate.length()
            val alreadyOptimal = compressedSize >= originalSize
            if (alreadyOptimal) {
                // Never claim compression when output is larger or equal — preserve original bytes
                tempCandidate.delete()
                inputFile.copyTo(outputFile, overwrite = true)
                onProgress(1f, "Document is already optimal")
                PdfCompressionStats(
                    outputFile = outputFile,
                    originalBytes = originalSize,
                    processedBytes = outputFile.length(),
                    alreadyOptimal = true,
                    imagesOptimized = optimizedImages
                )
            } else {
                if (outputFile.exists()) outputFile.delete()
                tempCandidate.renameTo(outputFile)
                onProgress(1f, "Compression complete")
                PdfCompressionStats(
                    outputFile = outputFile,
                    originalBytes = originalSize,
                    processedBytes = outputFile.length(),
                    alreadyOptimal = false,
                    imagesOptimized = optimizedImages
                )
            }
        }
    }

    suspend fun imagesToPdf(
        imageFiles: List<File>,
        outputFile: File,
        pageSizeOption: PdfPageSizeOption,
        customWidthPt: Float,
        customHeightPt: Float,
        landscape: Boolean,
        marginPt: Float,
        fitMode: ImageFitMode,
        jpegQuality: Int,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageFiles.isNotEmpty()) { "Select at least one image." }
            val baseW = if (pageSizeOption == PdfPageSizeOption.CUSTOM) customWidthPt.coerceIn(144f, 2400f) else pageSizeOption.widthPt
            val baseH = if (pageSizeOption == PdfPageSizeOption.CUSTOM) customHeightPt.coerceIn(144f, 2400f) else pageSizeOption.heightPt
            val pageW = if (landscape) max(baseW, baseH) else min(baseW, baseH)
            val pageH = if (landscape) min(baseW, baseH) else max(baseW, baseH)

            PDDocument().use { doc ->
                imageFiles.forEachIndexed { idx, imgFile ->
                    currentCoroutineContext().ensureActive()
                    val decoded = decodeSampledBitmap(imgFile, 2400)
                        ?: throw IllegalStateException("Could not decode ${imgFile.name}")
                    val oriented = ImageEngine.applyExifOrientation(imgFile, decoded)

                    val page = PDPage(PDRectangle(pageW, pageH))
                    doc.addPage(page)

                    val pdImage = if (jpegQuality >= 98 && imgFile.extension.equals("png", true)) {
                        LosslessFactory.createFromImage(doc, oriented)
                    } else {
                        JPEGFactory.createFromImage(doc, oriented, (jpegQuality.coerceIn(30, 100)) / 100f)
                    }

                    val contentW = (pageW - 2f * marginPt).coerceAtLeast(36f)
                    val contentH = (pageH - 2f * marginPt).coerceAtLeast(36f)
                    val imgW = oriented.width.toFloat().coerceAtLeast(1f)
                    val imgH = oriented.height.toFloat().coerceAtLeast(1f)

                    val (drawW, drawH) = when (fitMode) {
                        ImageFitMode.FIT -> {
                            val scale = min(contentW / imgW, contentH / imgH)
                            (imgW * scale) to (imgH * scale)
                        }
                        ImageFitMode.FILL -> {
                            val scale = max(contentW / imgW, contentH / imgH)
                            (imgW * scale) to (imgH * scale)
                        }
                        ImageFitMode.CENTER -> {
                            val scale = min(1f, min(contentW / imgW, contentH / imgH))
                            (imgW * scale) to (imgH * scale)
                        }
                    }

                    val drawX = (pageW - drawW) / 2f
                    val drawY = (pageH - drawH) / 2f

                    PDPageContentStream(doc, page).use { stream ->
                        stream.drawImage(pdImage, drawX, drawY, drawW, drawH)
                    }

                    oriented.recycle()
                    onProgress((idx + 1).toFloat() / imageFiles.size, "Added image ${idx + 1} of ${imageFiles.size}")
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun pdfToImages(
        inputFile: File,
        outputFolder: File,
        baseName: String,
        pageIndicesZeroBased: List<Int>,
        format: ImageFormatOption,
        dpi: Int,
        quality: Int,
        onProgress: (Float, String) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            require(pageIndicesZeroBased.isNotEmpty()) { "Select at least one page to export." }
            val targetWidth = when {
                dpi >= 300 -> 2200
                dpi >= 200 -> 1600
                else -> 1100
            }
            val outputs = mutableListOf<File>()
            ParcelFileDescriptor.open(inputFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val compressFormat = when (format) {
                        ImageFormatOption.PNG -> Bitmap.CompressFormat.PNG
                        ImageFormatOption.JPG -> Bitmap.CompressFormat.JPEG
                        ImageFormatOption.WEBP -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                    }
                    pageIndicesZeroBased.forEachIndexed { idx, pageIdx ->
                        currentCoroutineContext().ensureActive()
                        if (pageIdx in 0 until renderer.pageCount) {
                            renderer.openPage(pageIdx).use { page ->
                                val ratio = page.height.toFloat() / max(1, page.width).toFloat()
                                val width = targetWidth
                                val height = max(1, (width * ratio).roundToInt())
                                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).drawColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val outFile = buildUniqueFile(outputFolder, "${baseName}_page_${pageIdx + 1}.${format.extension}")
                                FileOutputStream(outFile).use { fos ->
                                    bmp.compress(compressFormat, quality.coerceIn(30, 100), fos)
                                }
                                bmp.recycle()
                                outputs.add(outFile)
                            }
                        }
                        onProgress((idx + 1).toFloat() / pageIndicesZeroBased.size, "Exported page ${pageIdx + 1}")
                    }
                }
            }
            outputs
        }
    }

    suspend fun organizePages(
        inputFile: File,
        pageSpecs: List<PdfPageEditSpec>,
        outputFile: File,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(pageSpecs.isNotEmpty()) { "At least one page must remain in the PDF." }
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { sourceDoc ->
                PDDocument().use { outDoc ->
                    pageSpecs.forEachIndexed { idx, spec ->
                        currentCoroutineContext().ensureActive()
                        val imported = outDoc.importPage(sourceDoc.getPage(spec.originalPageIndex))
                        if (spec.rotationDegrees != 0) {
                            val currentRot = imported.rotation
                            imported.rotation = (currentRot + spec.rotationDegrees) % 360
                        }
                        onProgress((idx + 1).toFloat() / pageSpecs.size, "Building page ${idx + 1}")
                    }
                    outDoc.save(outputFile)
                }
            }
            outputFile
        }
    }

    suspend fun protectPdf(
        inputFile: File,
        outputFile: File,
        userPassword: String,
        ownerPassword: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(userPassword.length >= 4) { "Password must be at least 4 characters." }
            val effectiveOwner = ownerPassword.ifBlank { userPassword }
            PDDocument.load(inputFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val accessPermission = AccessPermission().apply {
                    setCanPrint(true)
                    setCanExtractContent(true)
                }
                val policy = StandardProtectionPolicy(effectiveOwner, userPassword, accessPermission).apply {
                    encryptionKeyLength = 128
                    permissions = accessPermission
                }
                doc.protect(policy)
                doc.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun removePdfProtection(
        inputFile: File,
        outputFile: File,
        currentPassword: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(currentPassword.isNotBlank()) { "Enter the document password to unlock." }
            val doc = try {
                PDDocument.load(inputFile, currentPassword, MemoryUsageSetting.setupTempFileOnly())
            } catch (e: InvalidPasswordException) {
                throw IllegalStateException("Incorrect password. Authorization could not be verified.")
            }
            doc.use {
                it.isAllSecurityToBeRemoved = true
                it.save(outputFile)
            }
            outputFile
        }
    }

    suspend fun textToPdf(
        textContent: String,
        outputFile: File,
        title: String = "Document"
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(textContent.isNotBlank()) { "Text file is empty." }
            val pageSize = PDRectangle.A4
            val margin = 48f
            val fontSize = 11f
            val leading = 15.5f
            val usableWidth = pageSize.width - margin * 2f
            val font = PDType1Font.HELVETICA

            val wrappedLines = mutableListOf<String>()
            val rawLines = textContent.replace("\r\n", "\n").split("\n")
            for (paragraph in rawLines) {
                if (paragraph.isEmpty()) {
                    wrappedLines.add("")
                    continue
                }
                val words = paragraph.split(" ")
                var currentLine = StringBuilder()
                for (word in words) {
                    val safeWord = sanitizeWinAnsi(word)
                    val candidate = if (currentLine.isEmpty()) safeWord else "$currentLine $safeWord"
                    val width = runCatching { font.getStringWidth(candidate) / 1000f * fontSize }.getOrDefault(candidate.length * 6f)
                    if (width > usableWidth && currentLine.isNotEmpty()) {
                        wrappedLines.add(currentLine.toString())
                        currentLine = StringBuilder(safeWord)
                    } else {
                        currentLine = StringBuilder(candidate)
                    }
                }
                if (currentLine.isNotEmpty()) wrappedLines.add(currentLine.toString())
            }

            val linesPerPage = ((pageSize.height - margin * 2f) / leading).toInt().coerceAtLeast(10)
            val chunks = wrappedLines.chunked(linesPerPage)

            PDDocument().use { doc ->
                doc.documentInformation.title = title
                for (chunk in chunks) {
                    currentCoroutineContext().ensureActive()
                    val page = PDPage(pageSize)
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { stream ->
                        stream.beginText()
                        stream.setFont(font, fontSize)
                        stream.setLeading(leading)
                        stream.newLineAtOffset(margin, pageSize.height - margin)
                        for (line in chunk) {
                            stream.showText(line)
                            stream.newLine()
                        }
                        stream.endText()
                    }
                }
                doc.save(outputFile)
            }
            outputFile
        }
    }

    fun parseRangeGroups(rangesText: String, totalPages: Int): List<List<Int>> {
        if (rangesText.isBlank() || totalPages <= 0) return emptyList()
        val groups = mutableListOf<List<Int>>()
        rangesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
            if (token.contains("-")) {
                val parts = token.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (parts.size == 2) {
                    val start = parts[0].coerceIn(1, totalPages)
                    val end = parts[1].coerceIn(1, totalPages)
                    val range = if (start <= end) (start..end) else (end..start)
                    groups.add(range.map { it - 1 })
                }
            } else {
                val single = token.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    groups.add(listOf(single - 1))
                }
            }
        }
        return groups
    }

    private fun sanitizeWinAnsi(input: String): String {
        return input.map { ch ->
            if (ch.code in 32..126 || ch.code in 160..255) ch else ' '
        }.joinToString("")
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        val w = max(1, (bitmap.width * ratio).roundToInt())
        val h = max(1, (bitmap.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun decodeSampledBitmap(file: File, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var inSample = 1
        val maxSide = max(opts.outWidth, opts.outHeight)
        while (maxSide / inSample > maxDim * 2) {
            inSample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }

    private fun buildUniqueFile(folder: File, name: String): File {
        folder.mkdirs()
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = File(folder, name)
        var i = 1
        while (candidate.exists()) {
            candidate = File(folder, if (ext.isNotEmpty()) "${base}_$i.$ext" else "${base}_$i")
            i++
        }
        return candidate
    }
}
