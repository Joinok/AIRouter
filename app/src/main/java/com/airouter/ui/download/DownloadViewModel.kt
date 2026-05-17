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
 * 多模型下载 ViewModel
 * 支持同时下载多个模型，每个模型独立进度
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
    }

    private val modelsDir = File(application.filesDir, "models")

    // 下载状态集合：modelId -> 下载状态
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        // 初始化所有模型状态
        initAllModelStates()
    }

    /**
     * 初始化所有模型的状态（检查已下载的）
     */
    private fun initAllModelStates() {
        val catalog = com.airouter.data.model.ModelCatalog
        val states = mutableMapOf<String, DownloadState>()
        catalog.models.forEach { model ->
            val modelFile = File(modelsDir, model.fileName)
            states[model.id] = if (modelFile.exists()) {
                DownloadState(
                    status = DownloadStatus.COMPLETED,
                    progress = 100.0,
                    modelPath = modelFile.absolutePath
                )
            } else {
                DownloadState(status = DownloadStatus.IDLE, progress = 0.0)
            }
        }
        _downloadStates.value = states
    }

    /**
     * 获取指定模型的状态
     */
    fun getState(modelId: String): DownloadState {
        return _downloadStates.value[modelId] ?: DownloadState()
    }

    /**
     * 开始下载指定模型
     */
    fun startDownload(modelId: String) {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return

        updateState(modelId, DownloadState(
            status = DownloadStatus.DOWNLOADING,
            progress = 0.0,
            error = null
        ))

        viewModelScope.launch {
            downloadModel(modelEntry)
        }
    }

    /**
     * 实际执行下载
     */
    private suspend fun downloadModel(modelEntry: com.airouter.data.model.ModelCatalog.ModelEntry) {
        val modelFile = File(modelsDir, modelEntry.fileName)
        val filePath = modelFile.absolutePath
        val existingLength = if (modelFile.exists()) modelFile.length() else 0L

        try {
            withContext(Dispatchers.IO) {
                val url = URL(modelEntry.downloadUrl)
                var connection: HttpURLConnection? = null
                var input: java.io.InputStream? = null
                var output: java.io.OutputStream? = null
                try {
                    connection = url.openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "AIRouter/1.0")
                    connection.connectTimeout = CONNECT_TIMEOUT
                    connection.readTimeout = READ_TIMEOUT
                    connection.instanceFollowRedirects = true

                    // 断点续传
                    if (existingLength > 0) {
                        connection.setRequestProperty("Range", "bytes=$existingLength-")
                    }

                    connection.connect()
                    val responseCode = connection.responseCode

                    when {
                        responseCode == HttpURLConnection.HTTP_PARTIAL -> {
                            // 支持断点续传
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
                                    // 显示小数点后一位
                                    val progress = (totalRead * 100.0 / totalLength).coerceIn(0.0, 100.0)
                                    updateStateProgress(modelEntry.id, progress)
                                }
                            }
                        }
                        responseCode == HttpURLConnection.HTTP_OK -> {
                            // 不支持断点，从头下载
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
                                    val progress = (totalRead * 100.0 / contentLength).coerceIn(0.0, 100.0)
                                    updateStateProgress(modelEntry.id, progress)
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

            // 下载完成
            updateState(modelEntry.id, DownloadState(
                status = DownloadStatus.COMPLETED,
                progress = 100.0,
                modelPath = filePath
            ))
        } catch (e: Exception) {
            val errorMsg = e.message ?: "未知错误"
            updateState(modelEntry.id, DownloadState(
                status = DownloadStatus.FAILED,
                progress = 0.0,
                error = errorMsg
            ))
        }
    }

    /**
     * 更新单个模型的进度（线程安全）
     */
    private fun updateStateProgress(modelId: String, progress: Double) {
        val current = _downloadStates.value[modelId] ?: DownloadState(progress = 0.0)
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = current.copy(progress = progress)
        }
    }

    /**
     * 更新单个模型的完整状态
     */
    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = state
        }
    }

    /**
     * 删除指定模型文件
     */
    fun deleteModel(modelId: String) {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return
        val modelFile = File(modelsDir, modelEntry.fileName)
        if (modelFile.exists()) {
            modelFile.delete()
        }
        updateState(modelId, DownloadState(status = DownloadStatus.IDLE, progress = 0.0))
    }

    /**
     * 获取模型文件路径
     */
    fun getModelPath(modelId: String): String? {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return null
        val file = File(modelsDir, modelEntry.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * 检查模型是否已下载
     */
    fun isModelExists(modelId: String): Boolean {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return false
        return File(modelsDir, modelEntry.fileName).exists()
    }

    /**
     * 获取已下载的模型数量
     */
    fun getDownloadedCount(): Int {
        return _downloadStates.value.count { it.value.status == DownloadStatus.COMPLETED }
    }
}

/**
 * 下载状态
 */
data class DownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Double = 0.0,
    val error: String? = null,
    val modelPath: String? = null
)

/**
 * 下载状态枚举
 */
enum class DownloadStatus {
    IDLE,         // 空闲
    DOWNLOADING, // 下载中
    COMPLETED,   // 完成
    FAILED       // 失败
}