package com.airouter.ui.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * DownloadViewModel - 管理模型下载
 * 暂时使用简单的 URLConnection 下载，后续可替换为 Aria
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // 使用 ModelScope 国内镜像（魔搭社区），CDN 稳定
        const val MODEL_URL = "https://www.modelscope.cn/models/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/master/qwen2.5-3b-instruct-q8_0.gguf"
        const val MODEL_FILE_NAME = "qwen25_3b.gguf"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
    }

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val modelsDir = File(application.filesDir, "models")

    init {
        // 创建模型目录
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        // 检查模型是否已存在
        checkModelExists()
    }

    /**
     * 检查模型文件是否已存在
     */
    private fun checkModelExists() {
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        if (modelFile.exists()) {
            _downloadState.value = _downloadState.value.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                modelPath = modelFile.absolutePath
            )
        }
    }

    /**
     * 开始/继续下载模型（支持断点续传）
     */
    fun startDownload() {
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        val filePath = modelFile.absolutePath
        val existingLength = if (modelFile.exists()) modelFile.length() else 0L

        _downloadState.value = _downloadState.value.copy(
            status = DownloadStatus.DOWNLOADING,
            progress = if (existingLength > 0 && _downloadState.value.status != DownloadStatus.COMPLETED) {
                // 断点续传时保留已有进度估算
                _downloadState.value.progress
            } else 0,
            error = null
        )

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val url = URL(MODEL_URL)
                    var connection: HttpURLConnection? = null
                    var input: java.io.InputStream? = null
                    var output: java.io.OutputStream? = null
                    try {
                        connection = url.openConnection() as HttpURLConnection
                        connection.setRequestProperty("User-Agent", "AIRouter/1.0")
                        connection.connectTimeout = CONNECT_TIMEOUT
                        connection.readTimeout = READ_TIMEOUT
                        connection.instanceFollowRedirects = true

                        // 断点续传：设置 Range 头
                        if (existingLength > 0) {
                            connection.setRequestProperty("Range", "bytes=$existingLength-")
                        }

                        connection.connect()
                        val responseCode = connection.responseCode

                        // 检查是否支持断点续传
                        when {
                            responseCode == HttpURLConnection.HTTP_PARTIAL -> {
                                // 206 Partial Content，支持断点续传
                                val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                                val totalLength = existingLength + (if (contentLength > 0) contentLength else -1L)

                                input = connection.inputStream
                                output = if (existingLength > 0) java.io.FileOutputStream(modelFile, true) else modelFile.outputStream()
                                val buffer = ByteArray(BUFFER_SIZE)
                                var totalRead = existingLength

                                while (true) {
                                    val read = input!!.read(buffer)
                                    if (read == -1) break
                                    output!!.write(buffer, 0, read)
                                    totalRead += read
                                    if (totalLength > 0) {
                                        val progress = ((totalRead * 100 / totalLength).toInt()).coerceIn(0, 100)
                                        _downloadState.value = _downloadState.value.copy(progress = progress)
                                    }
                                }
                            }
                            responseCode == HttpURLConnection.HTTP_OK -> {
                                // 服务器不支持断点续传，从头下载
                                val contentLength = connection.contentLength.toLong()
                                input = connection.inputStream
                                output = modelFile.outputStream()
                                val buffer = ByteArray(BUFFER_SIZE)
                                var totalRead = 0L

                                while (true) {
                                    val read = input!!.read(buffer)
                                    if (read == -1) break
                                    output!!.write(buffer, 0, read)
                                    totalRead += read
                                    if (contentLength > 0) {
                                        val progress = ((totalRead * 100 / contentLength).toInt()).coerceIn(0, 100)
                                        _downloadState.value = _downloadState.value.copy(progress = progress)
                                    }
                                }
                            }
                            else -> {
                                throw Exception("HTTP 错误: $responseCode")
                            }
                        }

                        output?.flush()
                    } finally {
                        output?.close()
                        input?.close()
                        connection?.disconnect()
                    }
                }

                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    modelPath = filePath
                )
            } catch (e: Exception) {
                val errorMsg = e.localizedMessage ?: e.toString() ?: "未知错误"
                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.FAILED,
                    error = errorMsg
                )
            }
        }
    }

    /**
     * 获取模型文件路径
     */
    fun getModelPath(): String {
        return File(modelsDir, MODEL_FILE_NAME).absolutePath
    }

    /**
     * 检查模型是否存在
     */
    fun isModelExists(): Boolean {
        return File(modelsDir, MODEL_FILE_NAME).exists()
    }

    /**
     * 删除已下载的模型
     */
    fun deleteModel() {
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        if (modelFile.exists()) {
            modelFile.delete()
        }
        _downloadState.value = DownloadState()
    }

    /**
     * 取消下载
     */
    fun cancelDownload() {
        _downloadState.value = _downloadState.value.copy(
            status = DownloadStatus.IDLE,
            progress = 0
        )
    }
}

/**
 * 下载状态
 */
data class DownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Int = 0,
    val error: String? = null,
    val modelPath: String? = null
)

/**
 * 下载状态枚举
 */
enum class DownloadStatus {
    IDLE,      // 空闲
    DOWNLOADING, // 下载中
    COMPLETED,  // 完成
    FAILED      // 失败
}
