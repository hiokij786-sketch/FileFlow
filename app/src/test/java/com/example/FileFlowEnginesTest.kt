package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageFitMode
import com.example.data.model.ImageFormatOption
import com.example.data.model.PdfPageSizeOption
import com.example.engine.ImageEngine
import com.example.engine.ImageProcessOptions
import com.example.engine.NormalizedCropRect
import com.example.engine.PdfEngine
import com.example.engine.PdfPageEditSpec
import com.example.engine.ZipEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileFlowEnginesTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var pdfEngine: PdfEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        pdfEngine = PdfEngine(context)
        tempDir = File(context.cacheDir, "engine_test_${System.nanoTime()}").apply { mkdirs() }
    }

    @Test
    fun `textToPdf merge split organize info protect and unlock work end to end`() = runTest {
        val pdf1 = File(tempDir, "doc1.pdf")
        val pdf2 = File(tempDir, "doc2.pdf")

        // 1. TXT to PDF
        val text1 = (1..90).joinToString("\n") { "Line $it of FileFlow document alpha." }
        assertTrue(pdfEngine.textToPdf(text1, pdf1, "Alpha").isSuccess)
        assertTrue(pdfEngine.textToPdf("Single page beta document.", pdf2, "Beta").isSuccess)

        val info1 = pdfEngine.getPdfInfo(pdf1).getOrThrow()
        assertEquals(2, info1.pageCount)
        assertEquals("Alpha", info1.title)
        assertFalse(info1.isEncrypted)

        // 2. Merge PDFs
        val merged = File(tempDir, "merged.pdf")
        assertTrue(pdfEngine.mergePdfs(listOf(pdf1, pdf2), merged) { _, _ -> }.isSuccess)
        val mergedInfo = pdfEngine.getPdfInfo(merged).getOrThrow()
        assertEquals(3, mergedInfo.pageCount)

        // 3. Split by range
        val splitDir = File(tempDir, "split_out").apply { mkdirs() }
        val splitFiles = pdfEngine.splitPdfByRanges(merged, "1-2, 3", splitDir, "part") { _, _ -> }.getOrThrow()
        assertEquals(2, splitFiles.size)
        assertEquals(2, pdfEngine.getPdfInfo(splitFiles[0]).getOrThrow().pageCount)
        assertEquals(1, pdfEngine.getPdfInfo(splitFiles[1]).getOrThrow().pageCount)

        // 4. Organize pages (reverse & rotate)
        val organized = File(tempDir, "organized.pdf")
        val specs = listOf(PdfPageEditSpec(2, 90), PdfPageEditSpec(0, 0))
        assertTrue(pdfEngine.organizePages(merged, specs, organized) { _, _ -> }.isSuccess)
        assertEquals(2, pdfEngine.getPdfInfo(organized).getOrThrow().pageCount)

        // 5. Protect & Authorized Unlock
        val protectedPdf = File(tempDir, "protected.pdf")
        assertTrue(pdfEngine.protectPdf(pdf2, protectedPdf, "secret123", "owner123").isSuccess)
        assertTrue(pdfEngine.getPdfInfo(protectedPdf, "secret123").getOrThrow().isEncrypted)

        // Wrong password must fail
        val wrongUnlock = pdfEngine.removePdfProtection(protectedPdf, File(tempDir, "bad.pdf"), "wrong")
        assertTrue(wrongUnlock.isFailure)

        // Correct password succeeds
        val unlockedPdf = File(tempDir, "unlocked.pdf")
        assertTrue(pdfEngine.removePdfProtection(protectedPdf, unlockedPdf, "secret123").isSuccess)
        assertFalse(pdfEngine.getPdfInfo(unlockedPdf).getOrThrow().isEncrypted)

        // 6. Text extraction for search
        val pagesText = pdfEngine.extractTextPerPage(pdf2).getOrThrow()
        assertTrue(pagesText.first().contains("beta", ignoreCase = true))
    }

    @Test
    fun `imagesToPdf and pdfCompress work accurately`() = runTest {
        val imgFile = File(tempDir, "sample.png")
        val bmp = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.DKGRAY)
        FileOutputStream(imgFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val outPdf = File(tempDir, "from_img.pdf")
        val res = pdfEngine.imagesToPdf(
            imageFiles = listOf(imgFile),
            outputFile = outPdf,
            pageSizeOption = PdfPageSizeOption.A4,
            customWidthPt = 595f,
            customHeightPt = 842f,
            landscape = false,
            marginPt = 20f,
            fitMode = ImageFitMode.FIT,
            jpegQuality = 95
        ) { _, _ -> }
        assertTrue(res.isSuccess)
        assertEquals(1, pdfEngine.getPdfInfo(outPdf).getOrThrow().pageCount)

        val compressedPdf = File(tempDir, "compressed.pdf")
        val stats = pdfEngine.compressPdf(outPdf, compressedPdf, CompressionLevel.HIGH) { _, _ -> }.getOrThrow()
        assertTrue(stats.processedBytes <= stats.originalBytes)
    }

    @Test
    fun `imageEngine resize crop rotate and format conversion work`() = runTest {
        val srcFile = File(tempDir, "photo.png")
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.BLUE)
        FileOutputStream(srcFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()

        val outFile = File(tempDir, "photo_out.jpg")
        val report = ImageEngine.processImage(
            inputFile = srcFile,
            outputFile = outFile,
            options = ImageProcessOptions(
                outputFormat = ImageFormatOption.JPG,
                quality = 80,
                targetWidth = 100,
                targetHeight = 100,
                rotationDegrees = 90,
                cropRect = NormalizedCropRect(0.25f, 0f, 0.75f, 1f)
            )
        ).getOrThrow()

        assertTrue(outFile.exists())
        assertEquals(100, report.processedWidth)
        assertEquals(100, report.processedHeight)
    }

    @Test
    fun `zipEngine create list extract and block zip slip traversal`() = runTest {
        val f1 = File(tempDir, "a.txt").apply { writeText("Hello FileFlow A") }
        val f2 = File(tempDir, "b.txt").apply { writeText("Hello FileFlow B") }
        val zipFile = File(tempDir, "bundle.zip")

        assertTrue(ZipEngine.createZip(listOf(f1, f2), zipFile) { _, _ -> }.isSuccess)
        val entries = ZipEngine.listZipEntries(zipFile).getOrThrow()
        assertEquals(2, entries.size)

        val extractDir = File(tempDir, "extracted").apply { mkdirs() }
        val extracted = ZipEngine.extractZip(
            zipFile = zipFile,
            destDir = extractDir,
            selectedEntries = setOf("a.txt"),
            renamedFileMap = mapOf("a.txt" to "a_renamed.txt")
        ) { _, _ -> }.getOrThrow()
        assertEquals(1, extracted.size)
        assertEquals("a_renamed.txt", extracted.first().name)
        assertEquals("Hello FileFlow A", extracted.first().readText())

        // Zip Slip security test
        val evilZip = File(tempDir, "evil.zip")
        ZipOutputStream(FileOutputStream(evilZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../../outside.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }
        val evilResult = ZipEngine.extractZip(evilZip, extractDir) { _, _ -> }
        assertTrue(evilResult.isFailure)
        assertTrue(evilResult.exceptionOrNull() is SecurityException)
    }
}
