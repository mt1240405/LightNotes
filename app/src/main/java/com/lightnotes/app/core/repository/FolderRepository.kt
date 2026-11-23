package com.lightnotes.app.core.repository

import com.lightnotes.app.core.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeAllFolders(): Flow<List<Folder>>
    suspend fun getAllFoldersSync(): List<Folder>
    suspend fun getFolderByGlobalId(globalId: String): Folder?
    suspend fun createFolder(name: String, color: String = "#FFB200"): Folder
    suspend fun updateFolder(folder: Folder): Folder
    suspend fun deleteFolder(globalId: String)
}
