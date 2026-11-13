package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FileFlowApplication
import com.example.data.local.LinkedSafFolderEntity
import com.example.data.model.CompressionLevel
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.FolderInfo
import com.example.data.model.ImageFormatOption
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.StorageOverview
import com.example.data.model.ThemeMode
import com.example.data.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FileFlowApplication
    val workspaceRepo = app.workspaceRepository
    val prefsRepo = app.preferencesRepository
    val pdfEngine = app.pdfEngine

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val allFiles: StateFlow<List<FileItem>> = workspaceRepo.observeWorkspaceFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val linkedFolders: StateFlow<List<LinkedSafFolderEntity>> = workspaceRepo.observeLinkedFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _folders = MutableStateFlow<List<FolderInfo>>(emptyList())
    val folders: StateFlow<List<FolderInfo>> = _folders.asStateFlow()

    private val _storageOverview = MutableStateFlow<StorageOverview?>(null)
    val storageOverview: StateFlow<StorageOverview?> = _storageOverview.asStateFlow()

    private val _pendingExportFile = MutableStateFlow<FileItem?>(null)
    val pendingExportFile: StateFlow<FileItem?> = _pendingExportFile.asStateFlow()

    init {
        refreshFolders()
        refreshStorageOverview()
    }

    fun refreshFolders() {
        viewModelScope.launch {
            _folders.value = workspaceRepo.listFolders()
        }
    }

    fun refreshStorageOverview() {
        viewModelScope.launch {
            _storageOverview.value = workspaceRepo.getStorageOverview()
            _folders.value = workspaceRepo.listFolders()
        }
    }

    suspend fun importUris(uris: List<Uri>, folderName: String = "Imported"): List<FileItem> {
        val imported = mutableListOf<FileItem>()
        for (uri in uris) {
            workspaceRepo.importUriToWorkspace(uri, folderName).onSuccess {
                imported.add(it)
            }
        }
        refreshFolders()
        refreshStorageOverview()
        return imported
    }

    fun markOnboardingDone() {
        viewModelScope.launch { prefsRepo.setOnboardingCompleted(true) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setDefaultOutputFolder(folder: String) {
        viewModelScope.launch { prefsRepo.setDefaultOutputFolder(folder) }
    }

    fun setDefaultImageFormat(format: ImageFormatOption) {
        viewModelScope.launch { prefsRepo.setDefaultImageFormat(format) }
    }

    fun setDefaultPdfQuality(quality: CompressionLevel) {
        viewModelScope.launch { prefsRepo.setDefaultPdfQuality(quality) }
    }

    fun setSortPreferences(field: SortField, order: SortOrder) {
        viewModelScope.launch { prefsRepo.setSortPreferences(field, order) }
    }

    fun setGridView(isGrid: Boolean) {
        viewModelScope.launch { prefsRepo.setGridView(isGrid) }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch { workspaceRepo.toggleFavorite(item) }
    }

    fun markAccessed(item: FileItem) {
        viewModelScope.launch { workspaceRepo.markAccessed(item.path, item.name) }
    }

    fun createFolder(name: String, onResult: (Result<File>) -> Unit = {}) {
        viewModelScope.launch {
            val res = workspaceRepo.createFolder(name)
            refreshFolders()
            onResult(res)
        }
    }

    fun renameFile(item: FileItem, newName: String, onResult: (Result<FileItem>) -> Unit = {}) {
        viewModelScope.launch {
            val res = workspaceRepo.renameFile(item, newName)
            refreshFolders()
            refreshStorageOverview()
            onResult(res)
        }
    }

    fun copyFile(item: FileItem, targetFolder: String, onResult: (Result<FileItem>) -> Unit = {}) {
        viewModelScope.launch {
            val res = workspaceRepo.copyFileToFolder(item, targetFolder)
            refreshFolders()
            refreshStorageOverview()
            onResult(res)
        }
    }

    fun moveFile(item: FileItem, targetFolder: String, onResult: (Result<FileItem>) -> Unit = {}) {
        viewModelScope.launch {
            val res = workspaceRepo.moveFileToFolder(item, targetFolder)
            refreshFolders()
            refreshStorageOverview()
            onResult(res)
        }
    }

    fun deleteFile(item: FileItem, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val res = workspaceRepo.deleteFile(item)
            refreshFolders()
            refreshStorageOverview()
            onResult(res)
        }
    }

    fun clearTemporaryCache(onCleared: (Long) -> Unit) {
        viewModelScope.launch {
            val cleared = workspaceRepo.clearTemporaryFiles()
            refreshStorageOverview()
            onCleared(cleared)
        }
    }

    fun linkSafFolder(uri: Uri, onResult: (Result<LinkedSafFolderEntity>) -> Unit) {
        viewModelScope.launch {
            onResult(workspaceRepo.linkSafFolder(uri))
        }
    }

    fun unlinkSafFolder(uriString: String) {
        viewModelScope.launch { workspaceRepo.removeLinkedSafFolder(uriString) }
    }

    fun setPendingExportFile(item: FileItem?) {
        _pendingExportFile.value = item
    }

    fun exportPendingToUri(destUri: Uri, onDone: (Result<Unit>) -> Unit) {
        val item = _pendingExportFile.value ?: return
        viewModelScope.launch {
            val res = workspaceRepo.exportFileToUri(File(item.path), destUri)
            _pendingExportFile.value = null
            onDone(res)
        }
    }

    fun shareFile(item: FileItem) {
        val ctx = getApplication<Application>()
        runCatching {
            val intent = if (item.isExternalSaf) {
                Intent(Intent.ACTION_SEND).apply {
                    type = item.mimeType
                    putExtra(Intent.EXTRA_STREAM, Uri.parse(item.path))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                workspaceRepo.createShareIntent(File(item.path), item.mimeType)
            }
            val chooser = Intent.createChooser(intent, "Share ${item.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        }.onFailure {
            Toast.makeText(ctx, "Unable to share file: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openExternal(item: FileItem) {
        val ctx = getApplication<Application>()
        runCatching {
            val intent = if (item.isExternalSaf) {
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(item.path), item.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                workspaceRepo.createOpenExternalIntent(File(item.path), item.mimeType).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            ctx.startActivity(intent)
        }.onFailure {
            Toast.makeText(ctx, "No external app found to open ${item.extension.uppercase()} files.", Toast.LENGTH_SHORT).show()
        }
    }

    fun filterAndSort(
        files: List<FileItem>,
        query: String,
        category: FileCategory,
        sortField: SortField,
        sortOrder: SortOrder
    ): List<FileItem> {
        val q = query.trim().lowercase()
        val filtered = files.filter { item ->
            val catOk = category == FileCategory.ALL || item.category == category
            val queryOk = q.isEmpty() || item.name.lowercase().contains(q) ||
                item.folderName.lowercase().contains(q) ||
                (item.processTag?.lowercase()?.contains(q) == true)
            catOk && queryOk
        }

        val comparator = when (sortField) {
            SortField.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortField.SIZE -> compareBy<FileItem> { it.sizeBytes }
            SortField.DATE -> compareBy<FileItem> { it.lastModified }
            SortField.TYPE -> compareBy<FileItem> { it.extension }
        }
        return if (sortOrder == SortOrder.ASC) filtered.sortedWith(comparator)
        else filtered.sortedWith(comparator.reversed())
    }
}
