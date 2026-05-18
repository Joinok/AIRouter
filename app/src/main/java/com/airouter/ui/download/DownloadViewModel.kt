package com.airouter.ui.download

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 多模型下载 ViewModel
 * 支持同时下载多个模型，每个模型独立进度
 * 使用 applicationScope 保证下载在后台不被中断
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID_BASE = 1000
    }

    private val app = application
    private val modelsDir = File(application.filesDir, "models")
    private val notificationManager = application.getSystemService(NotificationManager::class.java)

    // 使用独立 scope，不随 ViewModel 销毁而取消
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 下载状态集合：modelId -> 下载状态
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // 正在下载的 job，用于取消
    private val downloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    init {
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        createNotificationChannel()
        initAllModelStates()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "模型下载",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "模型下载进度通知"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(modelId: String, model: com.airouter.data.model.ModelCatalog.ModelEntry?, progress: Int, status: DownloadStatus) {
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(model?.displayName ?: "模型下载")
            .setOngoing(status == DownloadStatus.DOWNLOADING)
            .setSilent(true)

        when (status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                builder.setProgress(100, progress, false)
                    .setContentText(if (status == DownloadStatus.PAUSED) "已暂停 $progress%" else "下载中 $progress%")
                    .setOngoing(status == DownloadStatus.DOWNLOADING)
            }
            DownloadStatus.COMPLETED -> {
                builder.setProgress(0, 0, false)
                    .setContentText("下载完成")
                    .setAutoCancel(true)
            }
            DownloadStatus.FAILED -> {
                builder.setProgress(0, 0, false)
                    .setContentText("下载失败")
                    .setAutoCancel(true)
            }
            else -> return
        }

        notificationManager.notify(NOTIFICATION_ID_BASE + modelId.hashCode() % 100, builder.build())
    }

    /**
     * 初始化所有模型的状态（检查已下载的，校验文件大小）
     */
    private fun initAllModelStates() {
        val catalog = com.airouter.data.model.ModelCatalog
        val states = mutableMapOf<String, DownloadState>()
        catalog.models.forEach { model ->
            val modelFile = File(modelsDir, model.fileName)
            if (modelFile.exists()) {
                // 校验文件完整性：文件大小 >= 预期大小的 90%
                val isValid = if (model.expectedSizeBytes > 0) {
                    modelFile.length() >= model.expectedSizeBytes * 0.9
                } else {
                    modelFile.length() > 0
                }
                states[model.id] = if (isValid) {
                    DownloadState(
                        status = DownloadStatus.COMPLETED,
                        progress = 100.0,
                        modelPath = modelFile.absolutePath
                    )
                } else {
                    // 文件不完整，标记为 IDLE，用户可以重新下载（支持断点续传）
                    DownloadState(
                        status = DownloadStatus.IDLE,
                        progress = 0.0,
                        error = null
                    )
                }
            } else {
                states[model.id] = DownloadState(status = DownloadStatus.IDLE, progress = 0.0)
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
        // 取消已有的下载 job
        downloadJobs[modelId]?.cancel()

        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return

        updateState(modelId, DownloadState(
            status = DownloadStatus.DOWNLOADING,
            progress = 0.0,
            error = null
        ))

        val job = downloadScope.launch {
            downloadModel(modelEntry)
        }
        downloadJobs[modelId] = job
    }

    /**
     * 暂停下载（取消协程但保留进度，支持断点续传）
     */
    fun pauseDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val currentProgress = _downloadStates.value[modelId]?.progress ?: 0.0
        updateState(modelId, DownloadState(status = DownloadStatus.PAUSED, progress = currentProgress))
        notificationManager.cancel(NOTIFICATION_ID_BASE + modelId.hashCode() % 100)
    }

    /**
     * 恢复下载
     */
    fun resumeDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return
        updateState(modelId, DownloadState(status = DownloadStatus.DOWNLOADING, progress = _downloadStates.value[modelId]?.progress ?: 0.0))
        val job = downloadScope.launch {
            downloadModel(modelEntry)
        }
        downloadJobs[modelId] = job
    }

    /**
     * 取消下载
     */
    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        // 取消时删除不完整文件
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId)
        if (modelEntry != null) {
            val modelFile = File(modelsDir, modelEntry.fileName)
            if (modelFile.exists()) modelFile.delete()
        }
        updateState(modelId, DownloadState(status = DownloadStatus.IDLE, progress = 0.0))
        notificationManager.cancel(NOTIFICATION_ID_BASE + modelId.hashCode() % 100)
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
                            var lastNotifyTime = 0L

                            while (true) {
                                val read = input!!.read(buffer)
                                if (read == -1) break
                                output!!.write(buffer, 0, read)
                                totalRead += read
                                if (totalLength > 0) {
                                    val progress = (totalRead * 100.0 / totalLength).coerceIn(0.0, 100.0)
                                    updateStateProgress(modelEntry.id, progress)
                                    // 通知栏进度（每 2 秒更新一次，减少性能开销）
                                    val now = System.currentTimeMillis()
                                    if (now - lastNotifyTime > 2000) {
                                        updateNotification(modelEntry.id, modelEntry, progress.toInt(), DownloadStatus.DOWNLOADING)
                                        lastNotifyTime = now
                                    }
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
                            var lastNotifyTime = 0L

                            while (true) {
                                val read = input!!.read(buffer)
                                if (read == -1) break
                                output!!.write(buffer, 0, read)
                                totalRead += read
                                if (contentLength > 0) {
                                    val progress = (totalRead * 100.0 / contentLength).coerceIn(0.0, 100.0)
                                    updateStateProgress(modelEntry.id, progress)
                                    val now = System.currentTimeMillis()
                                    if (now - lastNotifyTime > 2000) {
                                        updateNotification(modelEntry.id, modelEntry, progress.toInt(), DownloadStatus.DOWNLOADING)
                                        lastNotifyTime = now
                                    }
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

            // 下载完成：二次校验文件完整性
            val completedFile = File(modelsDir, modelEntry.fileName)
            val isComplete = if (modelEntry.expectedSizeBytes > 0) {
                completedFile.length() >= modelEntry.expectedSizeBytes * 0.9
            } else {
                completedFile.exists() && completedFile.length() > 0
            }

            if (isComplete) {
                updateState(modelEntry.id, DownloadState(
                    status = DownloadStatus.COMPLETED,
                    progress = 100.0,
                    modelPath = filePath
                ))
                updateNotification(modelEntry.id, modelEntry, 100, DownloadStatus.COMPLETED)
            } else {
                // 文件大小不符，标记为失败
                updateState(modelEntry.id, DownloadState(
                    status = DownloadStatus.FAILED,
                    progress = 0.0,
                    error = "文件大小不符（已下载 ${(completedFile.length() / 1_000_000)}MB，预期 ${modelEntry.expectedSizeBytes / 1_000_000}MB）"
                ))
                updateNotification(modelEntry.id, modelEntry, 0, DownloadStatus.FAILED)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 下载被取消（切后台等），标记为 IDLE 以便恢复
            updateState(modelEntry.id, DownloadState(
                status = DownloadStatus.IDLE,
                progress = 0.0
            ))
        } catch (e: Exception) {
            val errorMsg = e.message ?: "未知错误"
            updateState(modelEntry.id, DownloadState(
                status = DownloadStatus.FAILED,
                progress = 0.0,
                error = errorMsg
            ))
            updateNotification(modelEntry.id, modelEntry, 0, DownloadStatus.FAILED)
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
     * 检查模型是否已下载（且完整）
     */
    fun isModelExists(modelId: String): Boolean {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return false
        return com.airouter.data.model.ModelCatalog.isModelFileValid(modelsDir, modelEntry)
    }

    /**
     * 获取已下载的模型数量
     */
    fun getDownloadedCount(): Int {
        return _downloadStates.value.count { it.value.status == DownloadStatus.COMPLETED }
    }

    override fun onCleared() {
        // ViewModel 销毁时不取消下载，让 downloadScope 继续运行
        downloadJobs.clear()
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
    IDLE,         // 空闲（可恢复下载）
    DOWNLOADING, // 下载中
    PAUSED,      // 已暂停
    COMPLETED,   // 完成
    FAILED       // 失败
}
