package com.photouploader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photouploader.data.model.Photo
import com.photouploader.ui.theme.Green
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: Photo,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    isUploading: Boolean = false,
    isDownloading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // 缩放和旋转状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(photo.fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 上传按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onUpload,
                            enabled = !isUploading && !photo.isUploaded
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    if (photo.isUploaded) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                                    contentDescription = "上传",
                                    tint = if (photo.isUploaded) Green else Color.White
                                )
                            }
                        }
                        Text(
                            if (photo.isUploaded) "已上传" else "上传",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    
                    // 下载按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = onDownload,
                            enabled = !isDownloading && photo.cloudUrl != null
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "下载",
                                    tint = Color.White
                                )
                            }
                        }
                        Text(
                            "下载",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // 分享按钮
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconButton(
                            onClick = { /* 分享功能 */ },
                            enabled = photo.cloudUrl != null
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "分享",
                                tint = if (photo.cloudUrl != null) Color.White else Color.Gray
                            )
                        }
                        Text(
                            "分享",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(photo.localPath),
                contentDescription = photo.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
            
            // 双击重置缩放
            LaunchedEffect(Unit) {
                // 双击手势检测可以在这里添加
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这张照片吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun PhotoInfoSheet(
    photo: Photo,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "照片信息",
            style = MaterialTheme.typography.titleMedium
        )
        
        InfoRow("文件名", photo.fileName)
        InfoRow("大小", formatFileSize(photo.fileSize))
        InfoRow("添加时间", dateFormat.format(Date(photo.createdAt)))
        
        if (photo.uploadedAt != null) {
            InfoRow("上传时间", dateFormat.format(Date(photo.uploadedAt)))
        }
        
        if (photo.downloadedAt != null) {
            InfoRow("下载时间", dateFormat.format(Date(photo.downloadedAt)))
        }
        
        if (photo.cloudUrl != null) {
            InfoRow("云端地址", photo.cloudUrl, canCopy = true)
        }
        
        if (photo.isUploaded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "已上传到云端",
                    style = MaterialTheme.typography.bodySmall,
                    color = Green
                )
            }
        }
        
        if (photo.isDownloaded) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.DownloadDone,
                    contentDescription = null,
                    tint = Green,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "已从云端下载",
                    style = MaterialTheme.typography.bodySmall,
                    color = Green
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    canCopy: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1
        )
    }
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}
