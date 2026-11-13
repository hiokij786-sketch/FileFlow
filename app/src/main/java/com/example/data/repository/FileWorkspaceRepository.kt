package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.FileMetaEntity
import com.example.data.local.FileRecordDao
import com.example.data.local.LinkedSafFolderEntity
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.FolderInfo
import com.example.data.model.StorageOverview
import com.example.data.model.inferCategoryFromName
import com.example.data.model.inferMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class FileWorkspaceRepository(
    private val context: Context,
    private val dao: FileRecordDao
) {
    val workspaceRoot: File by lazy {
        File(context.filesDir, "FileFlow").apply { mkdirs() }
    }

    val tempDir: File by lazy {
        File(context.cacheDir, "fileflow_temp").apply { mkdirs() }
    }

    private val defaultFolderNames = listOf("Processed", "PDFs", "Images", "Scans", "Archives", "Converted")

    private val refreshTrigger = MutableStateFlow(0L)

    init {
        defaultFolderNames.forEach { File(workspaceRoot, it).mkdirs() }
    }

    fun triggerRefresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    fun observeWorkspaceFiles(): Flow<List<FileItem>> {
        return combine(dao.observeAllMeta(), refreshTrigger) { metaList, _ ->
            metaList.associateBy { it.filePath }
        }.map { metaMap ->
            withContext(Dispatchers.IO) {
                scanWorkspaceFiles(metaMap)
            }
        }
    }

    fun observeLinkedFolders(): Flow<List<LinkedSafFolderEntity>> = dao.observeLinkedFolders()

    private suspend fun scanWorkspaceFiles(metaMap: Map<String, FileMetaEntity>): List<FileItem> {
        defaultFolderNames.forEach { File(workspaceRoot, it).mkdirs() }
        val collected = mutableListOf<FileItem>()
        val validPaths = mutableListOf<String>()

        workspaceRoot.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { file ->
                validPaths.add(file.absolutePath)
                val meta = metaMap[file.absolutePath]
                val relFolder = file.parentFile?.relativeToOrSelf(workspaceRoot)?.path
                    ?.takeIf { it.isNotBlank() && it != "." } ?: "Root"
                val mime = inferMimeType(file.name)
                collected.add(
                    FileItem(
                        id = file.absolutePath,
                        name = file.name,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        mimeType = mime,
                        category = inferCategoryFromName(file.name, mime),
                        folderName = relFolder,
                        isFavorite = meta?.isFavorite ?: false,
                        isProcessed = meta?.isProcessed ?: false,
                        processTag = meta?.processTag,
                        originalSizeBytes = meta?.originalSizeBytes,
                        lastAccessedAt = meta?.lastAccessedAt ?: file.lastModified()
                    )
                )
            }

        if (validPaths.isNotEmpty()) {
            runCatching { dao.pruneMissingPaths(validPaths) }
        }
        return collected.sortedByDescending { it.lastModified }
    }

    suspend fun listFolders(): List<FolderInfo> = withContext(Dispatchers.IO) {
        defaultFolderNames.forEach { File(workspaceRoot, it).mkdirs() }
        workspaceRoot.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { dir ->
                val files = dir.walkTopDown().filter { it.isFile }.toList()
                FolderInfo(
                    name = dir.name,
                    path = dir.absolutePath,
                    fileCount = files.size,
                    totalBytes = files.sumOf { it.length() },
                    isCustom = dir.name !in defaultFolderNames
                )
            }
            ?.sortedWith(compareBy<FolderInfo> { it.isCustom }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    suspend fun createFolder(folderName: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = sanitizeFileName(folderName)
            require(sanitized.isNotBlank()) { "Folder name cannot be empty." }
            val dir = File(workspaceRoot, sanitized)
            if (dir.exists()) throw IllegalStateException("A folder named \"$sanitized\" already exists.")
            if (!dir.mkdirs()) throw IllegalStateException("Could not create folder.")
            triggerRefresh()
            dir
        }
    }

    fun getTargetFolder(folderName: String): File {
        val sanitized = sanitizeFileName(folderName).ifBlank { "Processed" }
        return File(workspaceRoot, sanitized).apply { mkdirs() }
    }

    fun buildUniqueOutputFile(folderName: String, desiredName: String): File {
        val dir = getTargetFolder(folderName)
        val clean = sanitizeFileName(desiredName).ifBlank { "output_${System.currentTimeMillis()}" }
        val base = clean.substringBeforeLast('.', clean)
        val ext = clean.substringAfterLast('.', "")
        var candidate = File(dir, clean)
        var index = 1
        while (candidate.exists()) {
            val suffixName = if (ext.isNotEmpty()) "${base}_$index.$ext" else "${base}_$index"
            candidate = File(dir, suffixName)
            index++
        }
        return candidate
    }

    fun createTempFile(prefix: String, extension: String): File {
        tempDir.mkdirs()
        val ext = extension.trimStart('.')
        return File(tempDir, "${prefix}_${System.currentTimeMillis()}.$ext")
    }

    suspend fun importUriToWorkspace(
        uri: Uri,
        targetFolder: String = "Imported"
    ): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveUriDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
            val destFile = buildUniqueOutputFile(targetFolder, displayName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Cannot read selected file.")

            val mime = context.contentResolver.getType(uri) ?: inferMimeType(destFile.name)
            dao.upsertMeta(
                FileMetaEntity(
                    filePath = destFile.absolutePath,
                    displayName = destFile.name,
                    isFavorite = false,
                    isProcessed = false,
                    lastAccessedAt = System.currentTimeMillis()
                )
            )
            triggerRefresh()
            FileItem(
                id = destFile.absolutePath,
                name = destFile.name,
                path = destFile.absolutePath,
                sizeBytes = destFile.length(),
                lastModified = destFile.lastModified(),
                mimeType = mime,
                category = inferCategoryFromName(destFile.name, mime),
                folderName = targetFolder
            )
        }
    }

    suspend fun copyUriToTemp(uri: Uri, fallbackExt: String = "tmp"): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveUriDisplayName(uri) ?: "temp_${System.currentTimeMillis()}.$fallbackExt"
            val ext = displayName.substringAfterLast('.', fallbackExt)
            val tempFile = createTempFile(displayName.substringBeforeLast('.').take(24), ext)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to open selected document.")
            tempFile
        }
    }

    suspend fun recordProcessedFile(
        outputFile: File,
        processTag: String,
        originalSizeBytes: Long? = null
    ): FileItem = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.upsertMeta(
            FileMetaEntity(
                filePath = outputFile.absolutePath,
                displayName = outputFile.name,
                isFavorite = false,
                isProcessed = true,
                processTag = processTag,
                originalSizeBytes = originalSizeBytes,
                lastAccessedAt = now
            )
        )
        triggerRefresh()
        val relFolder = outputFile.parentFile?.relativeToOrSelf(workspaceRoot)?.path
            ?.takeIf { it.isNotBlank() && it != "." } ?: "Processed"
        val mime = inferMimeType(outputFile.name)
        FileItem(
            id = outputFile.absolutePath,
            name = outputFile.name,
            path = outputFile.absolutePath,
            sizeBytes = outputFile.length(),
            lastModified = outputFile.lastModified(),
            mimeType = mime,
            category = inferCategoryFromName(outputFile.name, mime),
            folderName = relFolder,
            isFavorite = false,
            isProcessed = true,
            processTag = processTag,
            originalSizeBytes = originalSizeBytes,
            lastAccessedAt = now
        )
    }

    suspend fun markAccessed(filePath: String, displayName: String) = withContext(Dispatchers.IO) {
        val existing = dao.getMetaByPath(filePath)
        val now = System.currentTimeMillis()
        if (existing == null) {
            dao.upsertMeta(
                FileMetaEntity(
                    filePath = filePath,
                    displayName = displayName,
                    lastAccessedAt = now
                )
            )
        } else {
            dao.updateLastAccessed(filePath, now)
        }
        triggerRefresh()
    }

    suspend fun toggleFavorite(item: FileItem) = withContext(Dispatchers.IO) {
        val existing = dao.getMetaByPath(item.path)
        val nextFav = !(existing?.isFavorite ?: item.isFavorite)
        dao.upsertMeta(
            (existing ?: FileMetaEntity(filePath = item.path, displayName = item.name))
                .copy(isFavorite = nextFav)
        )
        triggerRefresh()
    }

    suspend fun renameFile(item: FileItem, newName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val oldFile = File(item.path)
            require(oldFile.exists()) { "Original file no longer exists." }
            val sanitized = sanitizeFileName(newName)
            require(sanitized.isNotBlank()) { "Filename cannot be empty." }
            val finalName = if (!sanitized.contains('.') && item.extension.isNotEmpty()) {
                "$sanitized.${item.extension}"
            } else {
                sanitized
            }
            val newFile = File(oldFile.parentFile, finalName)
            if (newFile.exists() && newFile.absolutePath != oldFile.absolutePath) {
                throw IllegalStateException("A file named \"$finalName\" already exists.")
            }
            if (!oldFile.renameTo(newFile)) {
                throw IllegalStateException("Could not rename file.")
            }
            val existingMeta = dao.getMetaByPath(oldFile.absolutePath)
            dao.deleteMeta(oldFile.absolutePath)
            dao.upsertMeta(
                (existingMeta ?: FileMetaEntity(newFile.absolutePath, newFile.name)).copy(
                    filePath = newFile.absolutePath,
                    displayName = newFile.name,
                    lastAccessedAt = System.currentTimeMillis()
                )
            )
            triggerRefresh()
            item.copy(
                id = newFile.absolutePath,
                name = newFile.name,
                path = newFile.absolutePath,
                lastModified = newFile.lastModified()
            )
        }
    }

    suspend fun copyFileToFolder(item: FileItem, targetFolderName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(item.path)
            require(src.exists()) { "Source file is missing." }
            val dest = buildUniqueOutputFile(targetFolderName, src.name)
            src.copyTo(dest, overwrite = false)
            dao.upsertMeta(
                FileMetaEntity(
                    filePath = dest.absolutePath,
                    displayName = dest.name,
                    isProcessed = item.isProcessed,
                    processTag = item.processTag,
                    originalSizeBytes = item.originalSizeBytes,
                    lastAccessedAt = System.currentTimeMillis()
                )
            )
            triggerRefresh()
            item.copy(
                id = dest.absolutePath,
                name = dest.name,
                path = dest.absolutePath,
                folderName = targetFolderName,
                lastModified = dest.lastModified()
            )
        }
    }

    suspend fun moveFileToFolder(item: FileItem, targetFolderName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        runCatching {
            val src = File(item.path)
            require(src.exists()) { "Source file is missing." }
            val dest = buildUniqueOutputFile(targetFolderName, src.name)
            src.copyTo(dest, overwrite = false)
            if (!src.delete()) {
                dest.delete()
                throw IllegalStateException("Unable to remove original file after moving.")
            }
            val oldMeta = dao.getMetaByPath(src.absolutePath)
            dao.deleteMeta(src.absolutePath)
            dao.upsertMeta(
                (oldMeta ?: FileMetaEntity(dest.absolutePath, dest.name)).copy(
                    filePath = dest.absolutePath,
                    displayName = dest.name,
                    lastAccessedAt = System.currentTimeMillis()
                )
            )
            triggerRefresh()
            item.copy(
                id = dest.absolutePath,
                name = dest.name,
                path = dest.absolutePath,
                folderName = targetFolderName,
                lastModified = dest.lastModified()
            )
        }
    }

    suspend fun deleteFile(item: FileItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(item.path)
            if (file.exists() && !file.delete()) {
                throw IllegalStateException("Could not delete ${item.name}.")
            }
            dao.deleteMeta(item.path)
            triggerRefresh()
        }
    }

    suspend fun exportFileToUri(sourceFile: File, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sourceFile.exists()) { "File not found." }
            context.contentResolver.openOutputStream(destUri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Could not open destination for saving.")
            Unit
        }
    }

    suspend fun linkSafFolder(treeUri: Uri): Result<LinkedSafFolderEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
                .onFailure {
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val name = doc?.name ?: "Device Folder"
            val entity = LinkedSafFolderEntity(treeUri = treeUri.toString(), displayName = name)
            dao.upsertLinkedFolder(entity)
            entity
        }
    }

    suspend fun removeLinkedSafFolder(treeUri: String) = withContext(Dispatchers.IO) {
        dao.deleteLinkedFolder(treeUri)
    }

    suspend fun listSafFolderFiles(treeUriString: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(treeUriString)
            val docDir = DocumentFile.fromTreeUri(context, uri)
                ?: throw IllegalStateException("Folder access expired or unavailable.")
            docDir.listFiles()
                .filter { it.isFile }
                .map { doc ->
                    val name = doc.name ?: "Unnamed"
                    val mime = doc.type ?: inferMimeType(name)
                    FileItem(
                        id = doc.uri.toString(),
                        name = name,
                        path = doc.uri.toString(),
                        sizeBytes = doc.length(),
                        lastModified = doc.lastModified(),
                        mimeType = mime,
                        category = inferCategoryFromName(name, mime),
                        folderName = docDir.name ?: "Linked Folder",
                        isExternalSaf = true
                    )
                }
                .sortedByDescending { it.lastModified }
        }
    }

    suspend fun getStorageOverview(): StorageOverview = withContext(Dispatchers.IO) {
        val statFs = StatFs(context.filesDir.absolutePath)
        val totalDevice = statFs.totalBytes
        val availDevice = statFs.availableBytes

        val files = scanWorkspaceFiles(emptyMap())
        val metaMap = mutableMapOf<String, FileMetaEntity>()
        // Compute breakdown
        val categoryMap = FileCategory.entries
            .filter { it != FileCategory.ALL }
            .associateWith { 0L }
            .toMutableMap()

        var workspaceTotal = 0L
        var processedBytes = 0L

        for (file in files) {
            val meta = dao.getMetaByPath(file.path)
            if (meta != null) metaMap[file.path] = meta
            workspaceTotal += file.sizeBytes
            if (meta?.isProcessed == true) {
                processedBytes += file.sizeBytes
            }
            categoryMap[file.category] = (categoryMap[file.category] ?: 0L) + file.sizeBytes
        }

        val tempBytes = (tempDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }) +
            (context.cacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L)

        val largeFiles = files.filter { it.sizeBytes >= 1_048_576L }
            .sortedByDescending { it.sizeBytes }
            .take(15)

        StorageOverview(
            deviceTotalBytes = totalDevice,
            deviceAvailableBytes = availDevice,
            workspaceTotalBytes = workspaceTotal,
            processedFilesBytes = processedBytes,
            tempCacheBytes = tempBytes,
            categoryBreakdown = categoryMap,
            largeFiles = largeFiles
        )
    }

    suspend fun clearTemporaryFiles(): Long = withContext(Dispatchers.IO) {
        var cleared = 0L
        tempDir.walkTopDown().filter { it.isFile }.forEach {
            val len = it.length()
            if (it.delete()) cleared += len
        }
        context.cacheDir.listFiles()?.forEach { child ->
            if (child.name != "image_manager_disk_cache" && child.isFile) {
                val len = child.length()
                if (child.delete()) cleared += len
            }
        }
        tempDir.mkdirs()
        cleared
    }

    fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val stat = StatFs(context.filesDir.absolutePath)
        if (stat.totalBytes <= 0L) return true
        return stat.availableBytes > requiredBytes + 4_194_304L // 4 MB safety cushion
    }

    fun getShareUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createShareIntent(file: File, mimeType: String): Intent {
        val uri = getShareUri(file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createOpenExternalIntent(file: File, mimeType: String): Intent {
        val uri = getShareUri(file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun resolveUriDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        }.getOrNull()
    }

    fun sanitizeFileName(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace("..", "_")
            .take(120)
    }
}
