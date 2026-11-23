package com.lightnotes.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE deleted_at_utc IS NULL ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deleted_at_utc IS NULL ORDER BY name ASC")
    suspend fun getAllFoldersSync(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE global_id = :globalId LIMIT 1")
    suspend fun getFolderByGlobalId(globalId: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name AND deleted_at_utc IS NULL LIMIT 1")
    suspend fun getFolderByName(name: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(folders: List<FolderEntity>): List<Long>

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE global_id = :globalId")
    suspend fun deleteByGlobalId(globalId: String)
}
