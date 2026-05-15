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

    init {
        // 如果提供了模型路径，尝试加载
        modelPath?.let {
            try {
                modelLoaded = engine.loadModel(it)
            } catch (e: Exception) {
                modelLoaded = false
            }
        }
    }

    override suspend fun listModels(): List<AiModel> {
        // 本地模型固定返回一个模型
        return listOf(
            AiModel(
                modelId = "local-qwen2.5-3b",
                displayName = "Qwen2.5 3B (Local)",
                description = "本地运行的 Qwen2.5 3B 模型"
            )
        )
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        if (!modelLoaded) {
            emit(ChatChunk(content = "[ERROR] 模型未加载，请先下载并加载模型"))
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
        if (!modelLoaded) {
            return ChatResponse(
                content = "[ERROR] 模型未加载，请先下载并加载模型",
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
        // 本地模型不需要验证 API Key
        return modelLoaded
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
