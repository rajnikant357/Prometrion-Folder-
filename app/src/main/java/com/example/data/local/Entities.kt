package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vaultFileName: String, // internal filename in vault directory
    val originalFileName: String,
    val originalPath: String,
    val originalSize: Long,
    val mimeType: String,
    val saltHex: String,
    val ivHex: String,
    val encryptedAt: Long = System.currentTimeMillis(),
    val category: String = "DOCUMENT",
    val note: String = ""
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentEntity(
    @PrimaryKey
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val fileType: String,
    val accessedAt: Long = System.currentTimeMillis()
)
