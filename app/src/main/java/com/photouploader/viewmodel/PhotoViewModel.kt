package com.photouploader.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.photouploader.data.model.CloudFile
import com.photouploader.data.model.Photo
import com.photouploader.data.model.UploadConfig
import com.photouploader.data.model.UploadType
import com.photouploader.data.repository.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class PhotoUiState(
    val photos: List<Photo> = emptyList(),
    val isLoading: Boolean = false,
    val uploadProgress: Map<String, Int> = emptyMap(),
    val downloadProgress: Map<String, Int> = emptyMap(),
    val uploadConfig: UploadConfig = UploadConfig(),
    val selectedPhotos: Set<String> = emptySet(),
    val error: String? = null,
    val successMessage: String? = null,
    val cloudFiles: List<CloudFile> = emptyList(),
    val isSyncing: Boolean = false,
    val currentPhoto: Photo? = null  // 当前查看的照片
)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = PhotoRepository(application)
    
    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
        loadConfig()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val photos = repository.loadPhotos()
            _uiState.value = _uiState.value.copy(photos = photos, isLoading = false)
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("settings", 0)
            val config = UploadConfig(
                endpoint = prefs.getString("endpoint", "") ?: "",
                apiKey = prefs.getString("apiKey", "") ?: "",
                type = try {
                    UploadType.valueOf(prefs.getString("uploadType", "NONE") ?: "NONE")
                } catch (e: Exception) {
                    UploadType.NONE
                }
            )
            _uiState.value = _uiState.value.copy(uploadConfig = config)
        }
    }

    fun saveConfig(config: UploadConfig) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("settings", 0)
            prefs.edit().apply {
                putString("endpoint", config.endpoint)
                putString("apiKey", config.apiKey)
                putString("uploadType", config.type.name)
                apply()
            }
            _uiState.value = _uiState.value.copy(uploadConfig = config)
        }
    }

    fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val newPhotos = mutableListOf<Photo>()
            uris.forEach { uri ->
                repository.savePhoto(uri)?.let { newPhotos.add(it) }
            }
            val allPhotos = (newPhotos + _uiState.value.photos).sortedByDescending { it.createdAt }
            repository.savePhotosList(allPhotos)
            _uiState.value = _uiState.value.copy(
                photos = allPhotos, 
                isLoading = false,
                successMessage = "成功添加 ${newPhotos.size} 张照片"
            )
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
            loadPhotos()
            _uiState.value = _uiState.value.copy(successMessage = "删除成功")
        }
    }

    fun deleteSelectedPhotos() {
        viewModelScope.launch {
            val count = _uiState.value.selectedPhotos.size
            _uiState.value.selectedPhotos.forEach { id ->
                repository.deletePhoto(id)
            }
            _uiState.value = _uiState.value.copy(
                selectedPhotos = emptySet(),
                successMessage = "成功删除 $count 张照片"
            )
            loadPhotos()
        }
    }

    fun toggleSelection(photoId: String) {
        val current = _uiState.value.selectedPhotos
        val new = if (current.contains(photoId)) {
            current - photoId
        } else {
            current + photoId
        }
        _uiState.value = _uiState.value.copy(selectedPhotos = new)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPhotos = emptySet())
    }

    // ========== 上传功能 ==========

    fun uploadPhoto(photoId: String) {
        viewModelScope.launch {
            val photo = _uiState.value.photos.find { it.id == photoId } ?: return@launch
            _uiState.value = _uiState.value.copy(
                uploadProgress = _uiState.value.uploadProgress + (photoId to 0)
            )
            
            val result = repository.uploadPhoto(photo, _uiState.value.uploadConfig)
            
            if (result.success) {
                val updatedPhoto = photo.copy(
                    isUploaded = true,
                    cloudUrl = result.cloudUrl,
                    uploadedAt = System.currentTimeMillis()
                )
                repository.updatePhoto(updatedPhoto)
                loadPhotos()
                _uiState.value = _uiState.value.copy(
                    successMessage = "${photo.fileName} 上传成功"
                )
            } else {
                _uiState.value = _uiState.value.copy(error = result.error)
            }
            
            _uiState.value = _uiState.value.copy(
                uploadProgress = _uiState.value.uploadProgress - photoId
            )
        }
    }

    fun uploadSelectedPhotos() {
        viewModelScope.launch {
            val count = _uiState.value.selectedPhotos.size
            var successCount = 0
            var failCount = 0
            
            _uiState.value.selectedPhotos.forEach { photoId ->
                val photo = _uiState.value.photos.find { it.id == photoId } ?: return@forEach
                _uiState.value = _uiState.value.copy(
                    uploadProgress = _uiState.value.uploadProgress + (photoId to 0)
                )
                
                val result = repository.uploadPhoto(photo, _uiState.value.uploadConfig)
                
                if (result.success) {
                    val updatedPhoto = photo.copy(
                        isUploaded = true,
                        cloudUrl = result.cloudUrl,
                        uploadedAt = System.currentTimeMillis()
                    )
                    repository.updatePhoto(updatedPhoto)
                    successCount++
                } else {
                    failCount++
                }
                
                _uiState.value = _uiState.value.copy(
                    uploadProgress = _uiState.value.uploadProgress - photoId
                )
            }
            
            clearSelection()
            loadPhotos()
            
            val message = when {
                failCount == 0 -> "成功上传 $successCount 张照片"
                successCount == 0 -> "上传失败"
                else -> "成功 $successCount 张，失败 $failCount 张"
            }
            _uiState.value = _uiState.value.copy(successMessage = message)
        }
    }

    // ========== 云端同步功能 ==========

    fun syncFromCloud() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            
            val result = repository.listCloudFiles(_uiState.value.uploadConfig)
            
            if (result.success) {
                _uiState.value = _uiState.value.copy(
                    cloudFiles = result.cloudFiles,
                    isSyncing = false,
                    successMessage = "云端共有 ${result.cloudFiles.size} 个文件"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    error = result.error
                )
            }
        }
    }

    // ========== 下载功能 ==========

    fun downloadFromCloud(cloudFile: CloudFile) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                downloadProgress = _uiState.value.downloadProgress + (cloudFile.name to 0)
            )
            
            val config = _uiState.value.uploadConfig
            val result = when (config.type) {
                UploadType.IMGBB, UploadType.CUSTOM_URL -> {
                    repository.downloadPhoto(cloudFile.path, config)
                }
                UploadType.WEBDAV -> {
                    repository.downloadPhoto(cloudFile.path, config)
                }
                UploadType.LATTICE_PANTRY -> {
                    repository.downloadPhoto(cloudFile.name, config)
                }
                UploadType.NONE -> {
                    com.photouploader.data.model.DownloadResult(false, error = "未配置上传服务")
                }
            }
            
            if (result.success && result.localPath != null) {
                // 创建新的 Photo 记录
                val file = java.io.File(result.localPath)
                val newPhoto = Photo(
                    id = UUID.randomUUID().toString(),
                    localPath = result.localPath,
                    cloudUrl = when (config.type) {
                        UploadType.LATTICE_PANTRY -> "${config.endpoint}/api/get/${cloudFile.name}"
                        else -> cloudFile.path
                    },
                    fileName = cloudFile.name,
                    fileSize = file.length(),
                    isDownloaded = true,
                    downloadedAt = System.currentTimeMillis()
                )
                
                // 添加到照片列表
                val photos = listOf(newPhoto) + _uiState.value.photos
                repository.savePhotosList(photos)
                loadPhotos()
                
                _uiState.value = _uiState.value.copy(
                    successMessage = "${cloudFile.name} 下载成功"
                )
            } else {
                _uiState.value = _uiState.value.copy(error = result.error)
            }
            
            _uiState.value = _uiState.value.copy(
                downloadProgress = _uiState.value.downloadProgress - cloudFile.name
            )
        }
    }

    fun downloadPhoto(photoId: String) {
        viewModelScope.launch {
            val photo = _uiState.value.photos.find { it.id == photoId } ?: return@launch
            val cloudUrl = photo.cloudUrl ?: return@launch
            
            _uiState.value = _uiState.value.copy(
                downloadProgress = _uiState.value.downloadProgress + (photoId to 0)
            )
            
            val result = repository.downloadPhoto(cloudUrl, _uiState.value.uploadConfig)
            
            if (result.success && result.localPath != null) {
                val updatedPhoto = photo.copy(
                    isDownloaded = true,
                    downloadedAt = System.currentTimeMillis()
                )
                repository.updatePhoto(updatedPhoto)
                loadPhotos()
                _uiState.value = _uiState.value.copy(
                    successMessage = "下载成功"
                )
            } else {
                _uiState.value = _uiState.value.copy(error = result.error)
            }
            
            _uiState.value = _uiState.value.copy(
                downloadProgress = _uiState.value.downloadProgress - photoId
            )
        }
    }

    // ========== 照片详情 ==========

    fun setCurrentPhoto(photo: Photo?) {
        _uiState.value = _uiState.value.copy(currentPhoto = photo)
    }

    // ========== 消息处理 ==========

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }

    // ========== 排序 ==========

    fun sortPhotos(by: SortBy) {
        viewModelScope.launch {
            val sorted = when (by) {
                SortBy.DATE_DESC -> _uiState.value.photos.sortedByDescending { it.createdAt }
                SortBy.DATE_ASC -> _uiState.value.photos.sortedBy { it.createdAt }
                SortBy.NAME_ASC -> _uiState.value.photos.sortedBy { it.fileName }
                SortBy.NAME_DESC -> _uiState.value.photos.sortedByDescending { it.fileName }
                SortBy.SIZE_DESC -> _uiState.value.photos.sortedByDescending { it.fileSize }
                SortBy.SIZE_ASC -> _uiState.value.photos.sortedBy { it.fileSize }
            }
            _uiState.value = _uiState.value.copy(photos = sorted)
        }
    }
}

enum class SortBy {
    DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC
}
