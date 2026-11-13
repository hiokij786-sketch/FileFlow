package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "file_metadata")
data class FileMetaEntity(
    @PrimaryKey val filePath: String,
    val displayName: String,
    val isFavorite: Boolean = false,
    val isProcessed: Boolean = false,
    val processTag: String? = null,
    val originalSizeBytes: Long? = null,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "linked_saf_folders")
data class LinkedSafFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val addedAt: Long = System.currentTimeMillis()
)
