package com.photouploader

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.photouploader.ui.screens.CloudFilesScreen
import com.photouploader.ui.screens.PhotoDetailScreen
import com.photouploader.ui.screens.PhotoGridScreen
import com.photouploader.ui.screens.SettingsScreen
import com.photouploader.ui.theme.PhotoUploaderTheme
import com.photouploader.viewmodel.PhotoViewModel
import com.photouploader.viewmodel.SortBy
import java.io.File

class MainActivity : ComponentActivity() {
    private var photoUri: Uri? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhotoUploaderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PhotoApp()
                }
            }
        }
    }
    
    @Composable
    fun PhotoApp() {
        val viewModel: PhotoViewModel = viewModel()
        val uiState by viewModel.uiState.collectAsState()
        
        // 当前屏幕: main(相册) / detail(详情) / settings(设置) / cloud(云端文件)
        var currentScreen by remember { mutableStateOf("main") }
        
        val imagePicker = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) { viewModel.addPhotos(uris) }
        }
        
        val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture()) { success ->
            if (success) { photoUri?.let { uri -> viewModel.addPhotos(listOf(uri)) } }
        }
        
        val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { }
        
        fun requestPermissions() {
            val permissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            if (permissions.isNotEmpty()) { permissionLauncher.launch(permissions.toTypedArray()) }
        }
        
        fun takePhoto() {
            val photoFile = File(cacheDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
            cameraLauncher.launch(photoUri)
        }
        
        LaunchedEffect(Unit) { requestPermissions() }
        
        var showAddDialog by remember { mutableStateOf(false) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        
        // 添加照片对话框
        if (showAddDialog) {
            AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("添加照片") }, text = { Text("选择图片来源") },
                confirmButton = { TextButton(onClick = { showAddDialog = false; imagePicker.launch("image/*") }) { Text("从相册选择") } },
                dismissButton = { TextButton(onClick = { showAddDialog = false; takePhoto() }) { Text("拍照") } })
        }
        
        // 删除确认对话框
        if (showDeleteDialog) {
            AlertDialog(onDismissRequest = { showDeleteDialog = false }, title = { Text("确认删除") },
                text = { Text("确定要删除选中的 ${uiState.selectedPhotos.size} 张照片吗？") },
                confirmButton = { TextButton(onClick = { viewModel.deleteSelectedPhotos(); showDeleteDialog = false }) { Text("删除") } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } })
        }
        
        // 根据当前屏幕显示不同界面
        when (currentScreen) {
            "main" -> {
                PhotoGridScreen(photos = uiState.photos, selectedPhotos = uiState.selectedPhotos, isLoading = uiState.isLoading, isSyncing = uiState.isSyncing,
                    error = uiState.error, successMessage = uiState.successMessage, uploadProgress = uiState.uploadProgress,
                    onPhotoClick = { photo -> if (uiState.selectedPhotos.isNotEmpty()) viewModel.toggleSelection(photo.id) else currentScreen = "detail"; viewModel.setCurrentPhoto(photo) },
                    onPhotoLongClick = { photoId -> viewModel.toggleSelection(photoId) },
                    onPhotoDoubleClick = { photo -> currentScreen = "detail"; viewModel.setCurrentPhoto(photo) },
                    onAddClick = { showAddDialog = true }, onSettingsClick = { currentScreen = "settings" }, onCloudClick = { viewModel.syncFromCloud(); currentScreen = "cloud" },
                    onDeleteClick = { showDeleteDialog = true }, onUploadClick = { viewModel.uploadSelectedPhotos() },
                    onSortClick = { viewModel.sortPhotos(SortBy.DATE_DESC) },
                    onClearError = { viewModel.clearError() }, onClearSuccess = { viewModel.clearSuccessMessage() })
            }
            
            "detail" -> {
                uiState.currentPhoto?.let { photo ->
                    PhotoDetailScreen(photo = photo, onBack = { currentScreen = "main"; viewModel.setCurrentPhoto(null) },
                        onUpload = { viewModel.uploadPhoto(photo.id) }, onDownload = { viewModel.downloadPhoto(photo.id) },
                        onDelete = { viewModel.deletePhoto(photo.id); currentScreen = "main" },
                        isUploading = uiState.uploadProgress.containsKey(photo.id), isDownloading = uiState.downloadProgress.containsKey(photo.id))
                } ?: run { currentScreen = "main" }
            }
            
            "settings" -> {
                SettingsScreen(config = uiState.uploadConfig, onSave = { config -> viewModel.saveConfig(config) }, onBack = { currentScreen = "main" })
            }
            
            "cloud" -> {
                CloudFilesScreen(cloudFiles = uiState.cloudFiles, isLoading = uiState.isSyncing, onBack = { currentScreen = "main" },
                    onRefresh = { viewModel.syncFromCloud() }, onDownload = { file -> viewModel.downloadFromCloud(file) },
                    downloadProgress = uiState.downloadProgress)
            }
        }
    }
}
