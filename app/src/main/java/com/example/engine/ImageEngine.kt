package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import androidx.compose.ui.geometry.Offset
import com.example.data.model.ImageFormatOption
import com.example.data.model.ScanFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImageDimensions(
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

data class NormalizedCropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

data class ImageProcessOptions(
    val outputFormat: ImageFormatOption = ImageFormatOption.JPG,
    val quality: Int = 85,
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val cropRect: NormalizedCropRect? = null
)

data class ImageProcessReport(
    val outputFile: File,
    val originalBytes: Long,
    val processedBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val processedWidth: Int,
    val processedHeight: Int
)

object ImageEngine {

    fun getImageDimensions(file: File): ImageDimensions {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return ImageDimensions(
            width = max(0, opts.outWidth),
            height = max(0, opts.outHeight),
            sizeBytes = file.length()
        )
    }

    fun applyExifOrientation(file: File, bitmap: Bitmap): Bitmap {
        val exif = runCatching { ExifInterface(file.absolutePath) }.getOrNull() ?: return bitmap
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    suspend fun processImage(
        inputFile: File,
        outputFile: File,
        options: ImageProcessOptions
    ): Result<ImageProcessReport> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFile.exists()) { "Input image does not exist." }
            val originalBytes = inputFile.length()
            val decoded = decodeSafeBitmap(inputFile, 4096)
                ?: throw IllegalStateException("Unsupported or corrupted image format.")
            var working = applyExifOrientation(inputFile, decoded)
            val origW = working.width
            val origH = working.height

            // 1. Crop
            options.cropRect?.let { crop ->
                val l = (crop.left.coerceIn(0f, 0.95f) * working.width).roundToInt()
                val t = (crop.top.coerceIn(0f, 0.95f) * working.height).roundToInt()
                val r = (crop.right.coerceIn(crop.left + 0.05f, 1f) * working.width).roundToInt()
                val b = (crop.bottom.coerceIn(crop.top + 0.05f, 1f) * working.height).roundToInt()
                val w = (r - l).coerceAtLeast(16)
                val h = (b - t).coerceAtLeast(16)
                if (l > 0 || t > 0 || w < working.width || h < working.height) {
                    val cropped = Bitmap.createBitmap(working, l, t, w, h)
                    if (cropped !== working) working.recycle()
                    working = cropped
                }
            }

            // 2. Rotate / Flip
            if (options.rotationDegrees % 360 != 0 || options.flipHorizontal) {
                val matrix = Matrix()
                if (options.flipHorizontal) {
                    matrix.postScale(-1f, 1f, working.width / 2f, working.height / 2f)
                }
                if (options.rotationDegrees % 360 != 0) {
                    matrix.postRotate((options.rotationDegrees % 360).toFloat())
                }
                val transformed = Bitmap.createBitmap(
                    working, 0, 0, working.width, working.height, matrix, true
                )
                if (transformed !== working) working.recycle()
                working = transformed
            }

            // 3. Resize
            val reqW = options.targetWidth
            val reqH = options.targetHeight
            if (reqW != null && reqH != null && reqW > 0 && reqH > 0 &&
                (reqW != working.width || reqH != working.height)
            ) {
                val resized = Bitmap.createScaledBitmap(working, reqW, reqH, true)
                if (resized !== working) working.recycle()
                working = resized
            }

            val finalW = working.width
            val finalH = working.height

            // 4. Write output
            val compressFormat = when (options.outputFormat) {
                ImageFormatOption.JPG -> Bitmap.CompressFormat.JPEG
                ImageFormatOption.PNG -> Bitmap.CompressFormat.PNG
                ImageFormatOption.WEBP -> @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }

            // If exporting to JPEG from transparent image, draw over white background
            val toSave = if (options.outputFormat == ImageFormatOption.JPG && working.hasAlpha()) {
                val flattened = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flattened)
                canvas.drawColor(android.graphics.Color.WHITE)
                canvas.drawBitmap(working, 0f, 0f, null)
                working.recycle()
                flattened
            } else {
                working
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { fos ->
                val ok = toSave.compress(compressFormat, options.quality.coerceIn(10, 100), fos)
                if (!ok) throw IllegalStateException("Failed to encode image.")
            }
            toSave.recycle()

            ImageProcessReport(
                outputFile = outputFile,
                originalBytes = originalBytes,
                processedBytes = outputFile.length(),
                originalWidth = origW,
                originalHeight = origH,
                processedWidth = finalW,
                processedHeight = finalH
            )
        }
    }

    suspend fun batchProcessImages(
        inputFiles: List<File>,
        outputDir: File,
        options: ImageProcessOptions,
        onProgress: (Float, String) -> Unit
    ): Result<List<ImageProcessReport>> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFiles.isNotEmpty()) { "Select at least one image." }
            outputDir.mkdirs()
            val reports = mutableListOf<ImageProcessReport>()
            inputFiles.forEachIndexed { idx, file ->
                currentCoroutineContext().ensureActive()
                val base = file.nameWithoutExtension
                var outFile = File(outputDir, "${base}_processed.${options.outputFormat.extension}")
                var suffix = 1
                while (outFile.exists()) {
                    outFile = File(outputDir, "${base}_processed_$suffix.${options.outputFormat.extension}")
                    suffix++
                }
                val report = processImage(file, outFile, options).getOrThrow()
                reports.add(report)
                onProgress((idx + 1).toFloat() / inputFiles.size, "Processed ${file.name}")
            }
            reports
        }
    }

    fun detectDocumentCornersHeuristic(bitmap: Bitmap): List<Offset> {
        // Lightweight edge-gradient bounding heuristic in normalized [0..1] coordinates
        val sampleW = 120
        val ratio = bitmap.height.toFloat() / max(1, bitmap.width).toFloat()
        val sampleH = max(40, (sampleW * ratio).roundToInt())
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        var minX = sampleW
        var maxX = 0
        var minY = sampleH
        var maxY = 0
        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        scaled.recycle()

        fun lum(px: Int): Int {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            return (r * 30 + g * 59 + b * 11) / 100
        }

        for (y in 2 until sampleH - 2) {
            for (x in 2 until sampleW - 2) {
                val c = lum(pixels[y * sampleW + x])
                val rx = lum(pixels[y * sampleW + (x + 1)])
                val dy = lum(pixels[(y + 1) * sampleW + x])
                val grad = abs(c - rx) + abs(c - dy)
                if (grad > 45) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        val spanX = maxX - minX
        val spanY = maxY - minY
        if (spanX < sampleW * 0.25f || spanY < sampleH * 0.25f) {
            return listOf(
                Offset(0.08f, 0.08f),
                Offset(0.92f, 0.08f),
                Offset(0.92f, 0.92f),
                Offset(0.08f, 0.92f)
            )
        }

        val l = (minX.toFloat() / sampleW).coerceIn(0.03f, 0.35f)
        val r = (maxX.toFloat() / sampleW).coerceIn(0.65f, 0.97f)
        val t = (minY.toFloat() / sampleH).coerceIn(0.03f, 0.35f)
        val b = (maxY.toFloat() / sampleH).coerceIn(0.65f, 0.97f)
        return listOf(
            Offset(l, t),
            Offset(r, t),
            Offset(r, b),
            Offset(l, b)
        )
    }

    fun warpPerspective(source: Bitmap, normalizedCorners: List<Offset>): Bitmap {
        if (normalizedCorners.size != 4) return source
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val tl = normalizedCorners[0]
        val tr = normalizedCorners[1]
        val br = normalizedCorners[2]
        val bl = normalizedCorners[3]

        val topWidth = kotlin.math.hypot((tr.x - tl.x) * w, (tr.y - tl.y) * h)
        val bottomWidth = kotlin.math.hypot((br.x - bl.x) * w, (br.y - bl.y) * h)
        val leftHeight = kotlin.math.hypot((bl.x - tl.x) * w, (bl.y - tl.y) * h)
        val rightHeight = kotlin.math.hypot((br.x - tr.x) * w, (br.y - tr.y) * h)

        val destW = max(topWidth, bottomWidth).roundToInt().coerceIn(64, 2400)
        val destH = max(leftHeight, rightHeight).roundToInt().coerceIn(64, 3200)

        val srcPoints = floatArrayOf(
            tl.x * w, tl.y * h,
            tr.x * w, tr.y * h,
            br.x * w, br.y * h,
            bl.x * w, bl.y * h
        )
        val dstPoints = floatArrayOf(
            0f, 0f,
            destW.toFloat(), 0f,
            destW.toFloat(), destH.toFloat(),
            0f, destH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val output = Bitmap.createBitmap(destW, destH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    fun applyScannerEnhancement(
        source: Bitmap,
        mode: ScanFilterMode,
        brightness: Float,
        contrast: Float,
        rotationDegrees: Int
    ): Bitmap {
        val rotated = if (rotationDegrees % 360 != 0) {
            val m = Matrix().apply { postRotate((rotationDegrees % 360).toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        } else {
            source.copy(Bitmap.Config.ARGB_8888, false)
        }

        val effectiveContrast = when (mode) {
            ScanFilterMode.ORIGINAL -> contrast
            ScanFilterMode.ENHANCED -> contrast * 1.25f
            ScanFilterMode.GRAYSCALE -> contrast * 1.15f
            ScanFilterMode.BLACK_WHITE -> contrast * 1.8f
        }
        val effectiveBrightness = when (mode) {
            ScanFilterMode.ORIGINAL -> brightness
            ScanFilterMode.ENHANCED -> brightness + 10f
            ScanFilterMode.GRAYSCALE -> brightness + 8f
            ScanFilterMode.BLACK_WHITE -> brightness + 20f
        }

        val cm = ColorMatrix()
        if (mode == ScanFilterMode.GRAYSCALE || mode == ScanFilterMode.BLACK_WHITE) {
            cm.setSaturation(0f)
        }
        val scale = effectiveContrast
        val translate = (-0.5f * scale + 0.5f) * 255f + effectiveBrightness
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bcMatrix)

        val result = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        rotated.recycle()

        if (mode == ScanFilterMode.BLACK_WHITE) {
            val w = result.width
            val h = result.height
            val px = IntArray(w * h)
            result.getPixels(px, 0, w, 0, 0, w, h)
            for (i in px.indices) {
                val c = px[i]
                val lum = (c shr 16) and 0xFF
                px[i] = if (lum > 142) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            }
            result.setPixels(px, 0, w, 0, 0, w, h)
        }
        return result
    }

    fun decodeSafeBitmap(file: File, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var inSample = 1
        val maxSide = max(opts.outWidth, opts.outHeight)
        while (maxSide / inSample > maxDim * 2) {
            inSample *= 2
        }
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = inSample }
        )
    }
}
