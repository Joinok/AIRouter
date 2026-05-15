package com.airouter.ui.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arialyy.aria.core.Aria
import com.arialyy.aria.core.task.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * DownloadViewModel - 管理模型下载
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q8_0.gguf"
        const val MODEL_FILE_NAME = "qwen25_3b.gguf"
    }

    private val _downloadState = MutableStateFlow(DownloadState())
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val modelsDir = File(application.filesDir, "models")

    init {
        // 初始化 Aria
        Aria.init(application)
        
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
     * 开始下载模型
     */
    fun startDownload() {
        val modelFile = File(modelsDir, MODEL_FILE_NAME)
        val filePath = modelFile.absolutePath

        _downloadState.value = _downloadState.value.copy(
            status = DownloadStatus.DOWNLOADING,
            progress = 0,
            error = null
        )

        viewModelScope.launch {
            try {
                // 使用 Aria 下载
                Aria.download(getApplication())
                    .load(MODEL_URL)
                    .setFilePath(filePath)
                    .create()
                
                // 注册下载监听
                // 注意：这里需要实际的 Aria 监听器实现
                // 暂时简化实现
                
            } catch (e: Exception) {
                _downloadState.value = _downloadState.value.copy(
                    status = DownloadStatus.FAILED,
                    error = e.message
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
