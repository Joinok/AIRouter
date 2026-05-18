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
 * 支持多个已下载的 GGUF 模型，包括多模态模型
 */
class LocalLLMProvider(
    private val context: Context,
    private val selectedModelId: String? = null
) : AIProvider {

    override val providerId: String = "local"
    override val providerName: String = "Local LLM"

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private var currentModelId: String? = null
    private var modelLoaded = false

    private fun getModelPathById(modelId: String): String? {
        val entry = ModelCatalog.findById(modelId) ?: return null
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, entry.fileName)
        return if (file.exists()) file.absolutePath else null
    }

    private fun getMmprojPathById(modelId: String): String? {
        val entry = ModelCatalog.findById(modelId) ?: return null
        if (!entry.isMultimodal || entry.mmprojFileName.isEmpty()) return null
        val modelsDir = File(context.filesDir, "models")
        val file = File(modelsDir, entry.mmprojFileName)
        return if (file.exists()) file.absolutePath else null
    }

    private fun getDefaultDownloadedModel(): String? {
        val modelsDir = File(context.filesDir, "models")
        val downloaded = ModelCatalog.getDownloadedIds(modelsDir)
        return downloaded.firstNotNullOfOrNull { getModelPathById(it) }
    }

    private fun loadModelById(modelId: String?): Boolean {
        val modelsDir = File(context.filesDir, "models")
        val entry = modelId?.let { ModelCatalog.findById(it) }

        if (entry != null) {
            if (!ModelCatalog.isModelFileValid(modelsDir, entry)) {
                return false
            }
        }

        val path = modelId?.let { getModelPathById(it) } ?: getDefaultDownloadedModel()
            ?: return false

        return try {
            engine.release()

            // 多模态模型需要同时加载 mmproj
            val mmprojPath = modelId?.let { getMmprojPathById(it) }
            if (mmprojPath != null) {
                modelLoaded = engine.loadMultimodalModel(path, mmprojPath)
            } else {
                modelLoaded = engine.loadModel(path)
            }

            if (modelLoaded) {
                currentModelId = modelId ?: "default"
            }
            modelLoaded
        } catch (e: Exception) {
            modelLoaded = false
            false
        }
    }

    @Synchronized
    private fun ensureModelLoaded(modelId: String?): Boolean {
        // 如果已加载同一个模型，直接返回
        if (modelLoaded && currentModelId == (modelId ?: "default")) return true
        return loadModelById(modelId)
    }

    override suspend fun listModels(): List<AiModel> {
        return ModelCatalog.models.map { entry ->
            val modelsDir = File(context.filesDir, "models")
            val isDownloaded = ModelCatalog.isModelFileValid(modelsDir, entry)
            val suffix = if (entry.isMultimodal) "👁" else ""
            AiModel(
                modelId = entry.id,
                displayName = if (isDownloaded) "${entry.displayName} (本地)" else "${entry.displayName} (未下载)"
            )
        }
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        val modelId = request.modelId.removePrefix("local-").takeIf { 
            ModelCatalog.findById(it) != null 
        }
        
        if (!ensureModelLoaded(modelId)) {
            val hint = if (getDefaultDownloadedModel() != null) {
                "[ERROR] 模型加载失败，请重启应用后重试"
            } else {
                "[ERROR] 模型未下载，请在设置中下载模型"
            }
            emit(ChatChunk(content = hint))
            return@flow
        }

        engine.resetChat()

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
        
        if (!ensureModelLoaded(modelId)) {
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
        return ensureModelLoaded(selectedModelId)
    }

    fun release() {
        engine.release()
        modelLoaded = false
        currentModelId = null
    }
}
