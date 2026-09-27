package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_items ORDER BY encryptedAt DESC")
    fun getAllVaultItems(): Flow<List<VaultEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: Long): VaultEntity?

    @Query("SELECT * FROM vault_items WHERE vaultFileName = :fileName LIMIT 1")
    suspend fun getVaultItemByFileName(fileName: String): VaultEntity?

    @Query("SELECT COUNT(*) FROM vault_items")
    fun getVaultCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultEntity): Long

    @Update
    suspend fun update(item: VaultEntity)

    @Delete
    suspend fun delete(item: VaultEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    fun isFavorite(path: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavoriteDirect(path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun removeFavorite(path: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recent_files ORDER BY accessedAt DESC LIMIT 30")
    fun getRecentFiles(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAccess(item: RecentEntity)

    @Query("DELETE FROM recent_files WHERE path = :path")
    suspend fun removeRecent(path: String)

    @Query("DELETE FROM recent_files")
    suspend fun clearAll()
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrash(): Flow<List<TrashEntity>>

    @Query("SELECT COUNT(*) FROM trash_items")
    fun getTrashCount(): Flow<Int>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun getTrashById(id: Long): TrashEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrashEntity): Long

    @Delete
    suspend fun delete(item: TrashEntity)

    @Query("DELETE FROM trash_items")
    suspend fun clearAll()
}

