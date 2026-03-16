package com.photouploader.data.model

import java.util.UUID

data class Photo(
    val id: String = UUID.randomUUID().toString(),
    val localPath: String,
    val cloudUrl: String? = null,
    val uploadedAt: Long? = null,
    val downloadedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val fileName: String,
    val fileSize: Long = 0,
    val isUploaded: Boolean = false,
    val isDownloaded: Boolean = false,
    val thumbnailPath: String? = null
)

data class UploadConfig(
    val endpoint: String = "",
    val apiKey: String = "",
    val type: UploadType = UploadType.NONE
)

enum class UploadType {
    NONE,
    WEBDAV,
    IMGBB,
    CUSTOM_URL,
    LATTICE_PANTRY  // 新增：格子食品柜（可自部署的简单网盘）
}

data class UploadResult(
    val success: Boolean,
    val cloudUrl: String? = null,
    val error: String? = null
)

data class CloudFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val isDirectory: Boolean = false
)

data class SyncResult(
    val success: Boolean,
    val cloudFiles: List<CloudFile> = emptyList(),
    val newPhotos: List<Photo> = emptyList(),
    val error: String? = null
)

data class DownloadResult(
    val success: Boolean,
    val localPath: String? = null,
    val error: String? = null
)
