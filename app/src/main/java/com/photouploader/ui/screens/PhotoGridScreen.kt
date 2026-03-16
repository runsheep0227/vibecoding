package com.photouploader.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photouploader.data.model.Photo
import com.photouploader.ui.theme.Green
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoGridScreen(
    photos: List<Photo>,
    selectedPhotos: Set<String>,
    isLoading: Boolean,
    isSyncing: Boolean,
    error: String?,
    successMessage: String?,
    uploadProgress: Map<String, Int>,
    onPhotoClick: (Photo) -> Unit,
    onPhotoLongClick: (String) -> Unit,
    onPhotoDoubleClick: (Photo) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCloudClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onUploadClick: () -> Unit,
    onSortClick: () -> Unit,
    onClearError: () -> Unit,
    onClearSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }
    
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            onClearSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("相册云盘") },
                actions = {
                    IconButton(onClick = onCloudClick, enabled = !isSyncing) {
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.CloudSync, contentDescription = "云端同步")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) { Icon(Icons.Default.Sort, contentDescription = "排序") }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("时间从新到旧") }, onClick = { showSortMenu = false; onSortClick() }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) })
                            DropdownMenuItem(text = { Text("时间从旧到新") }, onClick = { showSortMenu = false; onSortClick() }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) })
                            DropdownMenuItem(text = { Text("文件名 A-Z") }, onClick = { showSortMenu = false; onSortClick() }, leadingIcon = { Icon(Icons.Default.SortByAlpha, null) })
                        }
                    }
                    if (selectedPhotos.isNotEmpty()) {
                        IconButton(onClick = onUploadClick) { Icon(Icons.Default.CloudUpload, contentDescription = "上传") }
                        IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                    }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, contentDescription = "设置") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (photos.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("暂无照片", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击 + 添加照片", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(4.dp), modifier = Modifier.fillMaxSize()) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoGridItem(photo = photo, isSelected = selectedPhotos.contains(photo.id), isUploading = uploadProgress.containsKey(photo.id),
                            onClick = { onPhotoClick(photo) }, onLongClick = { onPhotoLongClick(photo.id) }, onDoubleClick = { onPhotoDoubleClick(photo) }, modifier = Modifier.animateItem())
                    }
                }
            }
            if (selectedPhotos.isNotEmpty()) {
                Surface(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("已选择 ${selectedPhotos.size} 张", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(photo: Photo, isSelected: Boolean, isUploading: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onDoubleClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick, onDoubleClick = onDoubleClick)) {
        AsyncImage(model = File(photo.thumbnailPath ?: photo.localPath), contentDescription = photo.fileName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        when {
            isUploading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp), strokeWidth = 2.dp)
            photo.isUploaded -> Icon(Icons.Default.CloudDone, "已上传", Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Green, CircleShape).padding(2.dp), tint = MaterialTheme.colorScheme.surface)
        }
        if (photo.isDownloaded) { Icon(Icons.Default.DownloadDone, "已下载", Modifier.align(Alignment.BottomStart).padding(4.dp).size(16.dp), tint = Green) }
        if (isSelected) {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
            Icon(Icons.Default.CheckCircle, "已选择", Modifier.align(Alignment.Center).size(32.dp), tint = MaterialTheme.colorScheme.surface)
        }
    }
}
