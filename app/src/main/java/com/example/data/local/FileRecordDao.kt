package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileRecordDao {
    @Query("SELECT * FROM file_metadata ORDER BY lastAccessedAt DESC")
    fun observeAllMeta(): Flow<List<FileMetaEntity>>

    @Query("SELECT * FROM file_metadata WHERE filePath = :path LIMIT 1")
    suspend fun getMetaByPath(path: String): FileMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: FileMetaEntity)

    @Query("UPDATE file_metadata SET isFavorite = :isFavorite WHERE filePath = :path")
    suspend fun setFavorite(path: String, isFavorite: Boolean)

    @Query("UPDATE file_metadata SET lastAccessedAt = :timestamp WHERE filePath = :path")
    suspend fun updateLastAccessed(path: String, timestamp: Long)

    @Query("DELETE FROM file_metadata WHERE filePath = :path")
    suspend fun deleteMeta(path: String)

    @Query("DELETE FROM file_metadata WHERE filePath NOT IN (:validPaths)")
    suspend fun pruneMissingPaths(validPaths: List<String>)

    @Query("SELECT * FROM linked_saf_folders ORDER BY addedAt DESC")
    fun observeLinkedFolders(): Flow<List<LinkedSafFolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLinkedFolder(folder: LinkedSafFolderEntity)

    @Query("DELETE FROM linked_saf_folders WHERE treeUri = :treeUri")
    suspend fun deleteLinkedFolder(treeUri: String)
}
