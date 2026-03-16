package com.photouploader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.photouploader.data.model.UploadConfig
import com.photouploader.data.model.UploadType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(config: UploadConfig, onSave: (UploadConfig) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var uploadType by remember { mutableStateOf(config.type) }
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var apiKey by remember { mutableStateOf(config.apiKey) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("设置") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
            actions = { TextButton(onClick = { onSave(UploadConfig(endpoint, apiKey, uploadType)); onBack() }) { Text("保存") } })
    }) { padding ->
        Column(modifier = modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("上传服务配置", style = MaterialTheme.typography.titleMedium)
            Text("选择上传类型", style = MaterialTheme.typography.bodyMedium)
            
            UploadType.entries.forEach { type ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = uploadType == type, onClick = { uploadType = type })
                    Column {
                        Text(text = when (type) {
                            UploadType.NONE -> "不启用上传"
                            UploadType.IMGBB -> "ImgBB (免费图片托管)"
                            UploadType.WEBDAV -> "WebDAV (个人网盘)"
                            UploadType.CUSTOM_URL -> "自定义 URL"
                            UploadType.LATTICE_PANTRY -> "格子食品柜 (自部署网盘)"
                        }, style = MaterialTheme.typography.bodyLarge)
                        Text(text = when (type) {
                            UploadType.NONE -> "仅本地存储"
                            UploadType.IMGBB -> "需要 API Key"
                            UploadType.WEBDAV -> "需要 WebDAV 服务器地址"
                            UploadType.CUSTOM_URL -> "支持任意 HTTP 上传接口"
                            UploadType.LATTICE_PANTRY -> "自部署的轻量级网盘服务"
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            HorizontalDivider()
            
            if (uploadType == UploadType.IMGBB) {
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("ImgBB API Key") }, placeholder = { Text("输入你的 ImgBB API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("获取 API Key: https://api.imgbb.com/", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            
            if (uploadType == UploadType.WEBDAV) {
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("WebDAV 地址") }, placeholder = { Text("https://your-nas.com/webdav/") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("用户名:密码 (Base64)") }, placeholder = { Text("输入 Base64 编码的认证信息") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("将用户名:密码 进行 Base64 编码后填入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            if (uploadType == UploadType.CUSTOM_URL) {
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("上传端点 URL") }, placeholder = { Text("https://your-server.com/upload") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key (可选)") }, placeholder = { Text("如需要认证") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            }

            if (uploadType == UploadType.LATTICE_PANTRY) {
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("格子食品柜服务器地址") }, placeholder = { Text("https://your-pantry-server.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, label = { Text("API Key") }, placeholder = { Text("输入 API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                Text("格子食品柜是一款可自部署的轻量级网盘，详见 GitHub", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            HorizontalDivider()
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Text("PhotoUploader v1.1", style = MaterialTheme.typography.bodyMedium)
            Text("相册云盘应用，支持本地存储、云端上传下载、缩略图生成。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
