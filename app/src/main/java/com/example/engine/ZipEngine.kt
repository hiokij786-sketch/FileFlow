package com.example.engine

import android.os.StatFs
import com.example.data.model.ZipEntryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipEngine {

    suspend fun listZipEntries(zipFile: File): Result<List<ZipEntryInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            require(zipFile.exists() && zipFile.length() > 0L) { "Archive file not found or empty." }
            val entries = mutableListOf<ZipEntryInfo>()
            ZipFile(zipFile).use { zf ->
                val enumEntries = zf.entries()
                while (enumEntries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = enumEntries.nextElement()
                    entries.add(
                        ZipEntryInfo(
                            name = entry.name,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory
                        )
                    )
                }
            }
            require(entries.isNotEmpty()) { "This ZIP archive contains no entries." }
            entries
        }
    }

    suspend fun extractZip(
        zipFile: File,
        destDir: File,
        selectedEntries: Set<String>? = null,
        renamedFileMap: Map<String, String> = emptyMap(),
        onProgress: (Float, String) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val entries = listZipEntries(zipFile).getOrThrow()
            val targetEntries = entries.filter { !it.isDirectory && (selectedEntries == null || it.name in selectedEntries) }
            require(targetEntries.isNotEmpty()) { "Select at least one file to extract." }

            val estimatedBytes = targetEntries.sumOf { it.sizeBytes.coerceAtLeast(1024L) }
            val stat = StatFs(destDir.apply { mkdirs() }.absolutePath)
            if (stat.totalBytes > 0L && stat.availableBytes < estimatedBytes + 2_097_152L) {
                throw IllegalStateException("Insufficient storage space to extract archive.")
            }

            val destCanonical = destDir.canonicalPath
            val extractedFiles = mutableListOf<File>()
            val totalCount = targetEntries.size
            var processedCount = 0

            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                val buffer = ByteArray(16 * 1024)
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    val entryName = entry.name
                    if (!entry.isDirectory && (selectedEntries == null || entryName in selectedEntries)) {
                        val customName = renamedFileMap[entryName]
                        val relativeTarget = if (!customName.isNullOrBlank()) {
                            val parent = entryName.substringBeforeLast('/', "")
                            if (parent.isNotEmpty()) "$parent/$customName" else customName
                        } else {
                            entryName
                        }

                        val outFile = File(destDir, relativeTarget)
                        val outCanonical = outFile.canonicalPath
                        // Zip Slip Path Traversal Protection
                        if (!outCanonical.startsWith(destCanonical + File.separator)) {
                            throw SecurityException("Blocked unsafe archive entry path: $entryName")
                        }

                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                var len: Int
                                while (zis.read(buffer).also { len = it } != -1) {
                                    currentCoroutineContext().ensureActive()
                                    bos.write(buffer, 0, len)
                                }
                            }
                        }
                        extractedFiles.add(outFile)
                        processedCount++
                        onProgress(processedCount.toFloat() / totalCount, "Extracted ${outFile.name}")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            extractedFiles
        }
    }

    suspend fun createZip(
        inputFiles: List<File>,
        outputZipFile: File,
        compressionLevel: Int = Deflater.DEFAULT_COMPRESSION,
        onProgress: (Float, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputFiles.isNotEmpty()) { "Select at least one file to archive." }
            val totalBytes = inputFiles.sumOf { it.length() }.coerceAtLeast(1L)
            val stat = StatFs(outputZipFile.parentFile?.apply { mkdirs() }?.absolutePath ?: "/")
            if (stat.totalBytes > 0L && stat.availableBytes < totalBytes + 2_097_152L) {
                throw IllegalStateException("Insufficient storage space to create ZIP archive.")
            }

            var bytesWritten = 0L
            val usedNames = mutableSetOf<String>()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
                zos.setLevel(compressionLevel)
                val buffer = ByteArray(16 * 1024)
                for (file in inputFiles) {
                    currentCoroutineContext().ensureActive()
                    if (!file.exists() || !file.isFile) continue
                    var entryName = file.name
                    var index = 1
                    while (entryName in usedNames) {
                        val base = file.nameWithoutExtension
                        val ext = file.extension
                        entryName = if (ext.isNotEmpty()) "${base}_$index.$ext" else "${base}_$index"
                        index++
                    }
                    usedNames.add(entryName)

                    zos.putNextEntry(ZipEntry(entryName))
                    BufferedInputStream(FileInputStream(file)).use { bis ->
                        var len: Int
                        while (bis.read(buffer).also { len = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            zos.write(buffer, 0, len)
                            bytesWritten += len
                            onProgress(
                                (bytesWritten.toFloat() / totalBytes).coerceIn(0f, 0.99f),
                                "Archiving ${file.name}"
                            )
                        }
                    }
                    zos.closeEntry()
                }
            }
            onProgress(1f, "Archive created")
            outputZipFile
        }
    }
}
