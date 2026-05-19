package com.airouter.ui.download

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
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
 * 多模态模型支持双文件下载（LLM + mmproj）
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

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

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

    private fun updateNotification(
        modelId: String,
        model: com.airouter.data.model.ModelCatalog.ModelEntry?,
        progress: Int,
        status: DownloadStatus,
        fileLabel: String = "",
        modelDisplayName: String = "",
        fileIndex: Int = 0,
        totalFiles: Int = 1
    ) {
        val builder = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(model?.displayName ?: "模型下载")
            .setOngoing(status == DownloadStatus.DOWNLOADING)
            .setSilent(true)

        when (status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED -> {
                builder.setProgress(100, progress, false)
                val text = when {
                    status == DownloadStatus.PAUSED -> "已暂停 $progress%"
                    modelDisplayName.isNotEmpty() && totalFiles > 1 ->
                        "正在下载 $modelDisplayName（${fileIndex + 1}/$totalFiles）：$fileLabel... $progress%"
                    modelDisplayName.isNotEmpty() ->
                        "正在下载 $modelDisplayName... $progress%"
                    fileLabel.isNotEmpty() -> "正在下载 $fileLabel... $progress%"
                    else -> "下载中 $progress%"
                }
                builder.setContentText(text)
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

    private fun initAllModelStates() {
        val catalog = com.airouter.data.model.ModelCatalog
        val states = mutableMapOf<String, DownloadState>()
        catalog.models.forEach { model ->
            val isValid = catalog.isModelFileValid(modelsDir, model)
            if (isValid) {
                states[model.id] = DownloadState(
                    status = DownloadStatus.COMPLETED,
                    progress = 100.0,
                    modelPath = File(modelsDir, model.fileName).absolutePath
                )
            } else {
                // 检查是否有部分下载（LLM 文件存在但不完整）
                val modelFile = File(modelsDir, model.fileName)
                val hasPartial = modelFile.exists() && modelFile.length() > 0
                states[model.id] = DownloadState(
                    status = DownloadStatus.IDLE,
                    progress = 0.0,
                    error = null
                )
            }
        }
        _downloadStates.value = states
    }

    fun getState(modelId: String): DownloadState {
        return _downloadStates.value[modelId] ?: DownloadState()
    }

    fun startDownload(modelId: String) {
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

    fun pauseDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val currentProgress = _downloadStates.value[modelId]?.progress ?: 0.0
        updateState(modelId, DownloadState(status = DownloadStatus.PAUSED, progress = currentProgress))
        notificationManager.cancel(NOTIFICATION_ID_BASE + modelId.hashCode() % 100)
    }

    fun resumeDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return
        updateState(modelId, DownloadState(status = DownloadStatus.DOWNLOADING, progress = _downloadStates.value[modelId]?.progress ?: 0.0))
        val job = downloadScope.launch {
            downloadModel(modelEntry)
        }
        downloadJobs[modelId] = job
    }

    fun cancelDownload(modelId: String) {
        downloadJobs[modelId]?.cancel()
        downloadJobs.remove(modelId)
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId)
        if (modelEntry != null) {
            File(modelsDir, modelEntry.fileName).let { if (it.exists()) it.delete() }
            if (modelEntry.isMultimodal && modelEntry.mmprojFileName.isNotEmpty()) {
                File(modelsDir, modelEntry.mmprojFileName).let { if (it.exists()) it.delete() }
            }
        }
        updateState(modelId, DownloadState(status = DownloadStatus.IDLE, progress = 0.0))
        notificationManager.cancel(NOTIFICATION_ID_BASE + modelId.hashCode() % 100)
    }

    /**
     * 下载单个文件（支持断点续传）
     * @return 下载的文件大小
     */
    private suspend fun downloadSingleFile(
        fileName: String,
        downloadUrl: String,
        expectedSize: Long,
        modelId: String,
        modelEntry: com.airouter.data.model.ModelCatalog.ModelEntry,
        progressOffset: Double,  // 进度偏移（多模态模型：LLM 0-50%, mmproj 50-100%）
        progressWeight: Double,   // 进度权重（1.0 = 独占, 0.5 = 占一半）
        fileLabel: String = "",   // 文件标签（"主模型"/"视觉编码器"）
        modelDisplayName: String = "",
        fileIndex: Int = 0,
        totalFiles: Int = 1
    ): Long {
        val modelFile = File(modelsDir, fileName)
        val existingLength = if (modelFile.exists()) modelFile.length() else 0L

        return withContext(Dispatchers.IO) {
            val url = URL(downloadUrl)
            var connection: HttpURLConnection? = null
            var input: java.io.InputStream? = null
            var output: java.io.OutputStream? = null
            var totalRead = existingLength

            try {
                connection = url.openConnection() as HttpURLConnection
                connection.setRequestProperty("User-Agent", "AIRouter/1.0")
                connection.connectTimeout = CONNECT_TIMEOUT
                connection.readTimeout = READ_TIMEOUT
                connection.instanceFollowRedirects = true

                if (existingLength > 0) {
                    connection.setRequestProperty("Range", "bytes=$existingLength-")
                }

                connection.connect()
                val responseCode = connection.responseCode

                when {
                    responseCode == HttpURLConnection.HTTP_PARTIAL -> {
                        val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                        val totalLength = existingLength + (if (contentLength > 0) contentLength else -1L)

                        input = connection.inputStream
                        output = if (existingLength > 0) java.io.FileOutputStream(modelFile, true) else modelFile.outputStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        var lastNotifyTime = 0L

                        while (true) {
                            val read = input!!.read(buffer)
                            if (read == -1) break
                            output!!.write(buffer, 0, read)
                            totalRead += read
                            if (totalLength > 0) {
                                val fileProgress = totalRead * 100.0 / totalLength
                                val overallProgress = progressOffset + fileProgress * progressWeight / 100.0 * 100.0
                                updateStateProgress(modelId, overallProgress.coerceIn(0.0, 99.9))
                                val now = System.currentTimeMillis()
                                if (now - lastNotifyTime > 2000) {
                                    updateNotification(modelId, modelEntry, overallProgress.toInt(), DownloadStatus.DOWNLOADING, fileLabel, modelDisplayName, fileIndex, totalFiles)
                                    lastNotifyTime = now
                                }
                            }
                        }
                    }
                    responseCode == HttpURLConnection.HTTP_OK -> {
                        val contentLength = connection.contentLength.toLong()
                        input = connection.inputStream
                        output = modelFile.outputStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        totalRead = 0L
                        var lastNotifyTime = 0L

                        while (true) {
                            val read = input!!.read(buffer)
                            if (read == -1) break
                            output!!.write(buffer, 0, read)
                            totalRead += read
                            if (contentLength > 0) {
                                val fileProgress = totalRead * 100.0 / contentLength
                                val overallProgress = progressOffset + fileProgress * progressWeight / 100.0 * 100.0
                                updateStateProgress(modelId, overallProgress.coerceIn(0.0, 99.9))
                                val now = System.currentTimeMillis()
                                if (now - lastNotifyTime > 2000) {
                                    updateNotification(modelId, modelEntry, overallProgress.toInt(), DownloadStatus.DOWNLOADING, fileLabel, modelDisplayName, fileIndex, totalFiles)
                                    lastNotifyTime = now
                                }
                            }
                        }
                    }
                    responseCode == 416 -> {
                        // Range Not Satisfiable: 服务器不支持断点续传或文件已完整
                        // 删掉部分文件，从头下载
                        android.util.Log.w("DownloadVM", "416 Range Not Satisfiable, restarting download from beginning")
                        modelFile.delete()
                        connection.disconnect()

                        // 重新建立连接，不带 Range header
                        connection = url.openConnection() as HttpURLConnection
                        connection.setRequestProperty("User-Agent", "AIRouter/1.0")
                        connection.connectTimeout = CONNECT_TIMEOUT
                        connection.readTimeout = READ_TIMEOUT
                        connection.instanceFollowRedirects = true
                        connection.connect()

                        val rc2 = connection.responseCode
                        if (rc2 != HttpURLConnection.HTTP_OK) {
                            throw Exception("HTTP 错误: $rc2 (重试下载)")
                        }

                        val contentLength = connection.contentLength.toLong()
                        input = connection.inputStream
                        output = modelFile.outputStream()
                        val buffer = ByteArray(BUFFER_SIZE)
                        totalRead = 0L
                        var lastNotifyTime = 0L

                        while (true) {
                            val read = input!!.read(buffer)
                            if (read == -1) break
                            output!!.write(buffer, 0, read)
                            totalRead += read
                            if (contentLength > 0) {
                                val fileProgress = totalRead * 100.0 / contentLength
                                val overallProgress = progressOffset + fileProgress * progressWeight / 100.0 * 100.0
                                updateStateProgress(modelId, overallProgress.coerceIn(0.0, 99.9))
                                val now = System.currentTimeMillis()
                                if (now - lastNotifyTime > 2000) {
                                    updateNotification(modelId, modelEntry, overallProgress.toInt(), DownloadStatus.DOWNLOADING, fileLabel, modelDisplayName, fileIndex, totalFiles)
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
                totalRead
            } finally {
                output?.close()
                input?.close()
                connection?.disconnect()
            }
        }
    }

    /**
     * 下载模型（支持多模态双文件）
     */
    private suspend fun downloadModel(modelEntry: com.airouter.data.model.ModelCatalog.ModelEntry) {
        try {
            if (modelEntry.isMultimodal && modelEntry.mmprojFileName.isNotEmpty()) {
                // 多模态模型：先下载 LLM（0-50%），再下载 mmproj（50-100%）
                downloadSingleFile(
                    fileName = modelEntry.fileName,
                    downloadUrl = modelEntry.downloadUrl,
                    expectedSize = modelEntry.expectedSizeBytes,
                    modelId = modelEntry.id,
                    modelEntry = modelEntry,
                    progressOffset = 0.0,
                    progressWeight = 0.5,
                    fileLabel = "主模型",
                    modelDisplayName = modelEntry.displayName,
                    fileIndex = 0,
                    totalFiles = 2
                )
                downloadSingleFile(
                    fileName = modelEntry.mmprojFileName,
                    downloadUrl = modelEntry.mmprojDownloadUrl,
                    expectedSize = modelEntry.mmprojExpectedSizeBytes,
                    modelId = modelEntry.id,
                    modelEntry = modelEntry,
                    progressOffset = 50.0,
                    progressWeight = 0.5,
                    fileLabel = "视觉编码器",
                    modelDisplayName = modelEntry.displayName,
                    fileIndex = 1,
                    totalFiles = 2
                )
            } else {
                // 纯文本模型：单文件下载（0-100%）
                downloadSingleFile(
                    fileName = modelEntry.fileName,
                    downloadUrl = modelEntry.downloadUrl,
                    expectedSize = modelEntry.expectedSizeBytes,
                    modelId = modelEntry.id,
                    modelEntry = modelEntry,
                    progressOffset = 0.0,
                    progressWeight = 1.0,
                    modelDisplayName = modelEntry.displayName,
                    fileIndex = 0,
                    totalFiles = 1
                )
            }

            // 校验完整性
            val isValid = com.airouter.data.model.ModelCatalog.isModelFileValid(modelsDir, modelEntry)

            if (isValid) {
                updateState(modelEntry.id, DownloadState(
                    status = DownloadStatus.COMPLETED,
                    progress = 100.0,
                    modelPath = File(modelsDir, modelEntry.fileName).absolutePath
                ))
                updateNotification(modelEntry.id, modelEntry, 100, DownloadStatus.COMPLETED)
            } else {
                updateState(modelEntry.id, DownloadState(
                    status = DownloadStatus.FAILED,
                    progress = 0.0,
                    error = "文件大小不符"
                ))
                updateNotification(modelEntry.id, modelEntry, 0, DownloadStatus.FAILED)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
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

    private fun updateStateProgress(modelId: String, progress: Double) {
        val current = _downloadStates.value[modelId] ?: DownloadState(progress = 0.0)
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = current.copy(progress = progress)
        }
    }

    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            this[modelId] = state
        }
    }

    fun deleteModel(modelId: String) {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return
        File(modelsDir, modelEntry.fileName).let { if (it.exists()) it.delete() }
        if (modelEntry.isMultimodal && modelEntry.mmprojFileName.isNotEmpty()) {
            File(modelsDir, modelEntry.mmprojFileName).let { if (it.exists()) it.delete() }
        }
        updateState(modelId, DownloadState(status = DownloadStatus.IDLE, progress = 0.0))
    }

    fun getModelPath(modelId: String): String? {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return null
        val file = File(modelsDir, modelEntry.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    fun isModelExists(modelId: String): Boolean {
        val modelEntry = com.airouter.data.model.ModelCatalog.findById(modelId) ?: return false
        return com.airouter.data.model.ModelCatalog.isModelFileValid(modelsDir, modelEntry)
    }

    fun getDownloadedCount(): Int {
        return _downloadStates.value.count { it.value.status == DownloadStatus.COMPLETED }
    }

    override fun onCleared() {
        downloadJobs.clear()
    }
}

data class DownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Double = 0.0,
    val error: String? = null,
    val modelPath: String? = null
)

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}
