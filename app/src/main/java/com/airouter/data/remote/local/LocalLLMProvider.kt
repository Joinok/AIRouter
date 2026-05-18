package com.airouter.data.remote.local

import android.content.Context
import com.airouter.data.model.AiModel
import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import com.airouter.data.model.TokenUsage
import com.airouter.data.model.ModelCatalog
import com.airouter.domain.provider.AIProvider
import com.airouter.lib.AiChat
import com.airouter.lib.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 本地 LLM Provider - 使用 llama.cpp 进行本地推理
 * 支持多个已下载的 GGUF 模型
 */
class LocalLLMProvider(
    private val context: Context,
    private val selectedModelId: String? = null  // 可选：指定加载哪个模型
) : AIProvider {

    override val providerId: String = "local"
    override val providerName: String = "Local LLM"

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private var currentModelId: String? = null
    private var modelLoaded = false

    /**
     * 获取模型文件路径（通过 modelId 查找）
     */
    private fun getModelPathById(modelId: String): String? {
        val entry = ModelCatalog.findById(modelId) ?: return null
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, entry.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * 获取默认已下载模型的路径（校验文件完整性）
     */
    private fun getDefaultDownloadedModel(): String? {
        val modelsDir = File(context.filesDir, "models")
        val downloaded = ModelCatalog.getDownloadedIds(modelsDir)
        return downloaded.firstNotNullOfOrNull { getModelPathById(it) }
    }

    /**
     * 根据 modelId 加载对应模型（先校验文件完整性）
     */
    private fun loadModelById(modelId: String?): Boolean {
        val modelsDir = File(context.filesDir, "models")
        val entry = modelId?.let { ModelCatalog.findById(it) }

        // 校验文件完整性
        if (entry != null) {
            if (!ModelCatalog.isModelFileValid(modelsDir, entry)) {
                return false
            }
        }

        val path = modelId?.let { getModelPathById(it) } ?: getDefaultDownloadedModel()
            ?: return false
        return try {
            engine.release()
            modelLoaded = engine.loadModel(path)
            if (modelLoaded) {
                currentModelId = modelId ?: "default"
            }
            modelLoaded
        } catch (e: Exception) {
            modelLoaded = false
            false
        }
    }

    /**
     * 确保模型已加载（懒加载）
     */
    @Synchronized
    private fun ensureModelLoaded(): Boolean {
        if (modelLoaded) return true
        return loadModelById(selectedModelId)
    }

    override suspend fun listModels(): List<AiModel> {
        // 返回 ModelCatalog 中所有模型（让用户看到完整列表可在 picker 中选择）
        // 未下载的模型会在聊天时提示用户下载
        return ModelCatalog.models.map { entry ->
            val modelsDir = File(context.filesDir, "models")
            val isDownloaded = File(modelsDir, entry.fileName).exists()
            AiModel(
                modelId = entry.id,
                displayName = if (isDownloaded) "${entry.displayName} (本地)" else "${entry.displayName} (未下载)"
            )
        }
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        // 根据请求的 modelId 加载对应模型
        val modelId = request.modelId.removePrefix("local-").takeIf { 
            ModelCatalog.findById(it) != null 
        }
        
        if (!loadModelById(modelId)) {
            val hint = if (getDefaultDownloadedModel() != null) {
                "[ERROR] 模型加载失败，请重启应用后重试"
            } else {
                "[ERROR] 模型未下载，请在设置中下载模型"
            }
            emit(ChatChunk(content = hint))
            return@flow
        }

        // 重置对话状态
        engine.resetChat()

        // 获取用户消息
        val userMsg = request.messages.lastOrNull { it.role.name.equals("USER", true) }?.content 
            ?: run {
                emit(ChatChunk(content = "[ERROR] 没有找到用户消息"))
                return@flow
            }

        try {
            engine.sendUserPrompt(userMsg).collect { token ->
                emit(ChatChunk(content = token))
            }
        } catch (e: Exception) {
            emit(ChatChunk(content = "[ERROR] 推理失败: ${e.message}"))
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val modelId = request.modelId.removePrefix("local-").takeIf { 
            ModelCatalog.findById(it) != null 
        }
        
        if (!loadModelById(modelId)) {
            return ChatResponse(
                content = if (getDefaultDownloadedModel() != null) {
                    "[ERROR] 模型加载失败，请重启应用后重试"
                } else {
                    "[ERROR] 模型未下载，请在设置中下载模型"
                },
                modelId = request.modelId
            )
        }

        return try {
            engine.resetChat()
            
            val userMsg = request.messages.lastOrNull { it.role.name.equals("USER", true) }?.content 
                ?: return ChatResponse(content = "[ERROR] 没有找到用户消息", modelId = request.modelId)

            val response = StringBuilder()
            engine.sendUserPrompt(userMsg).collect { token ->
                response.append(token)
            }

            ChatResponse(
                content = response.toString(),
                modelId = request.modelId,
                usage = TokenUsage()
            )
        } catch (e: Exception) {
            ChatResponse(
                content = "[ERROR] 推理失败: ${e.message}",
                modelId = request.modelId
            )
        }
    }

    override suspend fun validateApiKey(): Boolean {
        return ensureModelLoaded()
    }

    /**
     * 释放资源
     */
    fun release() {
        engine.release()
        modelLoaded = false
        currentModelId = null
    }
}