package com.photouploader.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.photouploader.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.UUID

class PhotoRepository(private val context: Context) {
    
    private val photosDir: File by lazy {
        File(context.filesDir, "photos").also { it.mkdirs() }
    }

    private val thumbnailDir: File by lazy {
        File(context.filesDir, "thumbnails").also { it.mkdirs() }
    }

    private val photosFile: File by lazy {
        File(context.filesDir, "photos.json")
    }

    suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        if (!photosFile.exists()) return@withContext emptyList()
        try {
            val json = photosFile.readText()
            val list = mutableListOf<Photo>()
            val array = JSONObject(json).getJSONArray("photos")
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(Photo(
                    id = obj.getString("id"),
                    localPath = obj.getString("localPath"),
                    cloudUrl = obj.optString("cloudUrl", null),
                    uploadedAt = obj.optLong("uploadedAt", 0).takeIf { it > 0 },
                    downloadedAt = obj.optLong("downloadedAt", 0).takeIf { it > 0 },
                    createdAt = obj.getLong("createdAt"),
                    fileName = obj.getString("fileName"),
                    fileSize = obj.getLong("fileSize"),
                    isUploaded = obj.getBoolean("isUploaded"),
                    isDownloaded = obj.optBoolean("isDownloaded", false),
                    thumbnailPath = obj.optString("thumbnailPath", null)
                ))
            }
            list.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun savePhoto(uri: Uri): Photo? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val fileName = "IMG_${System.currentTimeMillis()}.jpg"
            val destFile = File(photosDir, fileName)
            
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            // 生成缩略图
            val thumbnailPath = generateThumbnail(destFile, fileName)

            Photo(
                id = UUID.randomUUID().toString(),
                localPath = destFile.absolutePath,
                fileName = fileName,
                fileSize = destFile.length(),
                thumbnailPath = thumbnailPath
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun generateThumbnail(sourceFile: File, fileName: String): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(sourceFile.absolutePath, options)

            val targetSize = 200
            val scaleFactor = maxOf(
                options.outWidth / targetSize,
                options.outHeight / targetSize
            ).coerceAtLeast(1)

            options.inJustDecodeBounds = false
            options.inSampleSize = scaleFactor

            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, options) ?: return null
            val thumbnailFile = File(thumbnailDir, "thumb_$fileName")
            
            FileOutputStream(thumbnailFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            }
            bitmap.recycle()

            thumbnailFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deletePhoto(photoId: String): Boolean = withContext(Dispatchers.IO) {
        val photos = loadPhotos().toMutableList()
        val photo = photos.find { it.id == photoId } ?: return@withContext false
        
        File(photo.localPath).delete()
        photo.thumbnailPath?.let { File(it).delete() }
        photos.removeAll { it.id == photoId }
        savePhotosList(photos)
        true
    }

    suspend fun savePhotosList(photos: List<Photo>) = withContext(Dispatchers.IO) {
        val json = JSONObject()
        val array = org.json.JSONArray()
        photos.forEach { photo ->
            val obj = JSONObject().apply {
                put("id", photo.id)
                put("localPath", photo.localPath)
                put("cloudUrl", photo.cloudUrl)
                put("uploadedAt", photo.uploadedAt ?: 0)
                put("downloadedAt", photo.downloadedAt ?: 0)
                put("createdAt", photo.createdAt)
                put("fileName", photo.fileName)
                put("fileSize", photo.fileSize)
                put("isUploaded", photo.isUploaded)
                put("isDownloaded", photo.isDownloaded)
                put("thumbnailPath", photo.thumbnailPath)
            }
            array.put(obj)
        }
        json.put("photos", array)
        photosFile.writeText(json.toString())
    }

    suspend fun updatePhoto(photo: Photo) = withContext(Dispatchers.IO) {
        val photos = loadPhotos().toMutableList()
        val index = photos.indexOfFirst { it.id == photo.id }
        if (index >= 0) {
            photos[index] = photo
            savePhotosList(photos)
        }
    }

    // ========== 云端同步功能 ==========
    
    suspend fun listCloudFiles(config: UploadConfig): SyncResult = withContext(Dispatchers.IO) {
        if (config.type == UploadType.NONE || config.endpoint.isBlank()) {
            return@withContext SyncResult(false, error = "请先配置上传服务")
        }

        try {
            when (config.type) {
                UploadType.WEBDAV -> listWebDAVFiles(config.endpoint, config.apiKey)
                UploadType.LATTICE_PANTRY -> listPantryFiles(config.endpoint, config.apiKey)
                else -> SyncResult(false, error = "当前类型不支持列出文件")
            }
        } catch (e: Exception) {
            SyncResult(false, error = e.message)
        }
    }

    private fun listWebDAVFiles(endpoint: String, apiKey: String): SyncResult {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Basic $apiKey")
            .method("PROPFIND", null)
            .header("Depth", "1")
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val files = parseWebDAVResponse(body)
                SyncResult(true, cloudFiles = files)
            } else {
                SyncResult(false, error = "获取失败: ${response.code}")
            }
        }
    }

    private fun listPantryFiles(endpoint: String, apiKey: String): SyncResult {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$endpoint/api/list")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val files = parsePantryResponse(body)
                SyncResult(true, cloudFiles = files)
            } else {
                SyncResult(false, error = "获取失败: ${response.code}")
            }
        }
    }

    private fun parseWebDAVResponse(xml: String): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        try {
            val hrefPattern = "<d:href>(.*?)</d:href>".toRegex()
            val getlastModified = "<d:getlastmodified>(.*?)</d:getlastmodified>".toRegex()
            val getcontentLength = "<d:getcontentlength>(.*?)</d:getcontentlength>".toRegex()
            
            val hrefs = hrefPattern.findAll(xml)
            val matches = hrefs.map { it.groupValues[1] }.filter { !it.endsWith("/") }
            
            for (path in matches) {
                val name = path.substringAfterLast("/")
                if (name.isNotBlank() && !name.startsWith(".")) {
                    files.add(CloudFile(
                        name = name,
                        path = path,
                        size = 0,
                        lastModified = System.currentTimeMillis()
                    ))
                }
            }
        } catch (e: Exception) {
            // 解析失败
        }
        return files
    }

    private fun parsePantryResponse(json: String): List<CloudFile> {
        val files = mutableListOf<CloudFile>()
        try {
            val obj = JSONObject(json)
            val items = obj.getJSONObject("items")
            items.keys().forEach { key ->
                val item = items.getJSONObject(key)
                files.add(CloudFile(
                    name = key,
                    path = key,
                    size = item.optLong("size", 0),
                    lastModified = item.optLong("modified", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) {
            // 解析失败
        }
        return files
    }

    // ========== 下载功能 ==========

    suspend fun downloadPhoto(cloudUrl: String, config: UploadConfig): DownloadResult = withContext(Dispatchers.IO) {
        try {
            when (config.type) {
                UploadType.IMGBB -> downloadFromUrl(cloudUrl)
                UploadType.WEBDAV -> downloadFromWebDAV(cloudUrl, config.endpoint, config.apiKey)
                UploadType.LATTICE_PANTRY -> downloadFromPantry(cloudUrl, config.endpoint, config.apiKey)
                UploadType.CUSTOM_URL -> downloadFromUrl(cloudUrl)
                UploadType.NONE -> DownloadResult(false, error = "未配置上传服务")
            }
        } catch (e: Exception) {
            DownloadResult(false, error = e.message)
        }
    }

    private fun downloadFromUrl(url: String): DownloadResult {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).get().build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body ?: return@use DownloadResult(false, error = "空响应")
                val fileName = "cloud_${System.currentTimeMillis()}.jpg"
                val destFile = File(photosDir, fileName)
                
                FileOutputStream(destFile).use { output ->
                    body.byteStream().copyTo(output)
                }

                val thumbnailPath = generateThumbnail(destFile, fileName)

                DownloadResult(true, localPath = destFile.absolutePath)
            } else {
                DownloadResult(false, error = "下载失败: ${response.code}")
            }
        }
    }

    private fun downloadFromWebDAV(filePath: String, endpoint: String, apiKey: String): DownloadResult {
        val client = OkHttpClient()
        
        // 构建完整URL
        val url = if (filePath.startsWith("http")) filePath else {
            if (endpoint.endsWith("/")) "$endpoint$filePath" else "$endpoint/$filePath"
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Basic $apiKey")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body ?: return@use DownloadResult(false, error = "空响应")
                val fileName = filePath.substringAfterLast("/")
                val destFile = File(photosDir, fileName)
                
                FileOutputStream(destFile).use { output ->
                    body.byteStream().copyTo(output)
                }

                DownloadResult(true, localPath = destFile.absolutePath)
            } else {
                DownloadResult(false, error = "下载失败: ${response.code}")
            }
        }
    }

    private fun downloadFromPantry(fileName: String, endpoint: String, apiKey: String): DownloadResult {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("$endpoint/api/get/$fileName")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body ?: return@use DownloadResult(false, error = "空响应")
                val destFile = File(photosDir, fileName)
                
                FileOutputStream(destFile).use { output ->
                    body.byteStream().copyTo(output)
                }

                DownloadResult(true, localPath = destFile.absolutePath)
            } else {
                DownloadResult(false, error = "下载失败: ${response.code}")
            }
        }
    }

    // ========== 上传功能 ==========

    suspend fun uploadPhoto(photo: Photo, config: UploadConfig): UploadResult = withContext(Dispatchers.IO) {
        if (config.type == UploadType.NONE || config.endpoint.isBlank()) {
            return@withContext UploadResult(false, error = "请先配置上传服务")
        }

        val file = File(photo.localPath)
        if (!file.exists()) {
            return@withContext UploadResult(false, error = "文件不存在")
        }

        try {
            when (config.type) {
                UploadType.IMGBB -> uploadToImgBB(file, config.apiKey)
                UploadType.WEBDAV -> uploadToWebDAV(file, config.endpoint, config.apiKey)
                UploadType.CUSTOM_URL -> uploadToCustomURL(file, config.endpoint, config.apiKey)
                UploadType.LATTICE_PANTRY -> uploadToPantry(file, config.endpoint, config.apiKey)
                UploadType.NONE -> UploadResult(false, error = "未配置上传服务")
            }
        } catch (e: Exception) {
            UploadResult(false, error = e.message)
        }
    }

    private fun uploadToImgBB(file: File, apiKey: String): UploadResult {
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()

        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload?key=$apiKey")
            .post(requestBody)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                UploadResult(false, error = "上传失败: ${response.code}")
            } else {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                if (json.getBoolean("success")) {
                    val data = json.getJSONObject("data")
                    val url = data.getString("url")
                    UploadResult(true, cloudUrl = url)
                } else {
                    UploadResult(false, error = json.getString("error"))
                }
            }
        }
    }

    private fun uploadToWebDAV(file: File, endpoint: String, apiKey: String): UploadResult {
        val client = OkHttpClient()
        val requestBody = file.asRequestBody("image/jpeg".toMediaType())
        
        val fileName = file.name
        val url = if (endpoint.endsWith("/")) "$endpoint$fileName" else "$endpoint/$fileName"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Basic $apiKey")
            .put(requestBody)
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 201) {
                UploadResult(true, cloudUrl = url)
            } else {
                UploadResult(false, error = "上传失败: ${response.code}")
            }
        }
    }

    private fun uploadToPantry(file: File, endpoint: String, apiKey: String): UploadResult {
        val client = OkHttpClient()
        
        // 读取文件并 Base64 编码
        val bytes = file.readBytes()
        val base64 = Base64.getEncoder().encodeToString(bytes)
        
        val jsonBody = JSONObject().apply {
            put("file", base64)
            put("name", file.name)
        }

        val requestBody = RequestBody.create(
            "application/json".toMediaType(),
            jsonBody.toString()
        )

        val request = Request.Builder()
            .url("$endpoint/api/upload")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                UploadResult(true, cloudUrl = "${endpoint}/api/get/${file.name}")
            } else {
                UploadResult(false, error = "上传失败: ${response.code}")
            }
        }
    }

    private fun uploadToCustomURL(file: File, endpoint: String, apiKey: String): UploadResult {
        val client = OkHttpClient()
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
            .build()

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(requestBody)
        
        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                UploadResult(true, cloudUrl = body)
            } else {
                UploadResult(false, error = "上传失败: ${response.code}")
            }
        }
    }
}
