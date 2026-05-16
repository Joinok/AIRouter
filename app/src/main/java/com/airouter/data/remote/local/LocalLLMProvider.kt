package com.airouter.data.remote.local

import android.content.Context
import com.airouter.data.model.AiModel
import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import com.airouter.data.model.TokenUsage
import com.airouter.domain.provider.AIProvider
import com.airouter.lib.AiChat
import com.airouter.lib.InferenceEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * 本地 LLM Provider - 使用 llama.cpp 进行本地推理
 */
class LocalLLMProvider(
    private val context: Context,
    private val modelPath: String? = null
) : AIProvider {

    override val providerId: String = "local"
    override val providerName: String = "Local LLM"

    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)
    private var modelLoaded = false
    private val modelPathToUse: String? by lazy {
        modelPath ?: if (isModelExists(context)) getModelPath(context) else null
    }

    /**
     * 确保模型已加载（懒加载：首次使用时尝试加载）
     * 如果 init 时模型不存在，下载后再聊时会自动加载
     */
    @Synchronized
    private fun ensureModelLoaded(): Boolean {
        if (modelLoaded) return true
        val path = modelPathToUse ?: return false
        return try {
            modelLoaded = engine.loadModel(path)
            modelLoaded
        } catch (e: Exception) {
            modelLoaded = false
            false
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "qwen25_3b.gguf"

        /**
         * 计算模型文件路径（与 DownloadViewModel 保持一致）
         */
        fun getModelPath(context: Context): String {
            val modelsDir = File(context.filesDir, "models")
            return File(modelsDir, MODEL_FILE_NAME).absolutePath
        }

        /**
         * 检查模型文件是否存在
         */
        fun isModelExists(context: Context): Boolean {
            return File(getModelPath(context)).exists()
        }
    }

    override suspend fun listModels(): List<AiModel> {
        // 本地模型固定返回一个模型
        return listOf(
            AiModel(
                modelId = "local-qwen2.5-3b",
                displayName = "Qwen2.5 3B (Local)"
            )
        )
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        if (!ensureModelLoaded()) {
            val hint = if (isModelExists(context)) {
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

        // 发送消息并收集流式回复
        try {
            engine.sendUserPrompt(userMsg).collect { token ->
                emit(ChatChunk(content = token))
            }
        } catch (e: Exception) {
            emit(ChatChunk(content = "[ERROR] 推理失败: ${e.message}"))
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        if (!ensureModelLoaded()) {
            return ChatResponse(
                content = if (isModelExists(context)) {
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

            // 对于非流式，我们收集所有 token 并拼接
            val response = StringBuilder()
            engine.sendUserPrompt(userMsg).collect { token ->
                response.append(token)
            }

            ChatResponse(
                content = response.toString(),
                modelId = request.modelId,
                usage = TokenUsage() // 本地推理暂时无法准确统计 token
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
     * 加载模型
     */
    fun loadModel(path: String): Boolean {
        return try {
            modelLoaded = engine.loadModel(path)
            modelLoaded
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean = modelLoaded

    /**
     * 释放资源
     */
    fun release() {
        engine.release()
        modelLoaded = false
    }
}
