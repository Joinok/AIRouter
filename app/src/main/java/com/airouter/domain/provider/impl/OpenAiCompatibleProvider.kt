package com.airouter.domain.provider.impl

import android.util.Log
import com.airouter.data.model.AiModel
import com.airouter.debug.DebugLog
import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import com.airouter.data.model.MessageRole
import com.airouter.data.model.Provider
import com.airouter.data.model.TokenUsage
import com.airouter.data.remote.dto.ContentPart
import com.airouter.data.remote.dto.OpenAiChatRequest
import com.airouter.data.remote.dto.OpenAiContent
import com.airouter.data.remote.dto.OpenAiMessage
import com.airouter.data.remote.dto.StreamOptions
import com.airouter.data.remote.sse.SseEvent
import com.airouter.data.remote.sse.SseParser
import com.airouter.domain.provider.AIProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI 兼容协议 Provider。
 * DeepSeek、智谱、Moonshot、通义、SiliconFlow、Ollama 等都走这个。
 */
class OpenAiCompatibleProvider(
    private val provider: Provider,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : AIProvider {

    override val providerId: String = provider.id
    override val providerName: String = provider.name

    private val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
    private val apiKey = provider.apiKey

    override suspend fun listModels(): List<AiModel> {
        // 如果内置了模型列表，直接返回
        if (provider.supportedModels.isNotEmpty()) {
            return provider.supportedModels
        }

        // 从 /models API 获取
        try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw Exception("HTTP ${response.code}: $errorBody")
            }

            val body = response.body?.string() ?: throw Exception("响应体为空")
            val modelsResp = try {
                json.decodeFromString<com.airouter.data.remote.dto.OpenAiModelsResponse>(body)
            } catch (e: Exception) {
                throw Exception("解析响应失败: ${e.message}\n原始响应: ${body.take(500)}")
            }
            return modelsResp.data?.map { model ->
                AiModel(modelId = model.id, displayName = model.id)
            } ?: throw Exception("模型列表为空，响应: ${body.take(200)}")
        } catch (e: java.net.UnknownHostException) {
            throw Exception("DNS 解析失败: ${baseUrl}")
        } catch (e: java.net.SocketTimeoutException) {
            throw Exception("连接超时")
        } catch (e: java.net.ConnectException) {
            throw Exception("连接失败: ${e.message ?: "网络不可达"}")
        } catch (e: javax.net.ssl.SSLException) {
            throw Exception("SSL 错误: ${e.message}")
        } catch (e: Exception) {
            if (e.message != null) throw e
            throw Exception("网络错误: ${e::class.simpleName}")
        }
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        val openAiMessages = buildOpenAiMessages(request)

        // 检查模型是否有固定参数要求
        val model = provider.supportedModels.find { it.modelId == request.modelId }
        val temperature = model?.fixedTemperature ?: request.temperature
        val topP = model?.fixedTopP ?: request.topP
        // minMaxTokens 是推理模型建议的最小值，取其与用户设置的较大者
        val maxTokens = maxOf(model?.minMaxTokens ?: 0, request.maxTokens ?: 0)
        val logMsg = "chatStream: model=${request.modelId}, request.maxTokens=${request.maxTokens}, minMaxTokens=${model?.minMaxTokens}, effective maxTokens=$maxTokens, temperature=$temperature, topP=$topP"
        Log.d("OpenAiCompat", logMsg)
        DebugLog.log("API", logMsg)

        val openAiRequest = OpenAiChatRequest(
            model = request.modelId,
            messages = openAiMessages,
            temperature = temperature,
            max_tokens = maxTokens,
            top_p = topP,
            stream = true,
            stream_options = if (provider.supportsStreamOptions) StreamOptions() else null,
        )

        val body = json.encodeToString(OpenAiChatRequest.serializer(), openAiRequest)
        DebugLog.log("API", "POST $baseUrl/chat/completions")
        DebugLog.log("API", "Request body: ${body.take(500)}")
        Log.d("OpenAiCompat", "SSE request body: $body")

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${request.apiKey}",
        )

        val sseParser = SseParser(client, json)
        var flowCompleted = false  // 确保外层 flow 一定结束
        sseParser.streamChat("$baseUrl/chat/completions", headers, body)
            .collect { event ->
                when (event) {
                    is SseEvent.Chunk -> {
                        emit(ChatChunk(
                            content = event.content,
                            isReasoning = event.isReasoning,
                            finishReason = event.finishReason,
                            usage = event.usage,
                        ))
                    }
                    is SseEvent.Done -> {
                        DebugLog.log("SSE", "✓ [DONE]")
                        flowCompleted = true
                        return@collect
                    }
                    is SseEvent.Error -> {
                        DebugLog.log("SSE", "✗ Error: ${event.message}")
                        throw Exception(event.message)
                    }
                    is SseEvent.Connected -> {
                        DebugLog.log("SSE", "● Connected to $baseUrl/chat/completions")
                    }
                }
            }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val openAiMessages = buildOpenAiMessages(request)

        val model = provider.supportedModels.find { it.modelId == request.modelId }
        val temperature = model?.fixedTemperature ?: request.temperature
        val topP = model?.fixedTopP ?: request.topP
        val maxTokens = model?.minMaxTokens ?: request.maxTokens

        val openAiRequest = OpenAiChatRequest(
            model = request.modelId,
            messages = openAiMessages,
            temperature = temperature,
            max_tokens = maxTokens,
            top_p = topP,
            stream = false,
        )

        val body = json.encodeToString(OpenAiChatRequest.serializer(), openAiRequest)

        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer ${request.apiKey}")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val chatResponse = json.decodeFromString<com.airouter.data.remote.dto.OpenAiChatResponse>(responseBody)
        val choice = chatResponse.choices.firstOrNull() ?: throw Exception("No choices in response")

        val usage = chatResponse.usage?.let {
            TokenUsage(
                promptTokens = it.promptTokens,
                completionTokens = it.completionTokens,
                totalTokens = it.totalTokens,
            )
        } ?: TokenUsage()

        return ChatResponse(
            content = (choice.message?.content ?: "") as String,
            modelId = chatResponse.model ?: request.modelId,
            usage = usage,
        )
    }

    override suspend fun validateApiKey(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private fun buildOpenAiMessages(request: ChatRequest): List<OpenAiMessage> {
        val messages = mutableListOf<OpenAiMessage>()

        // System prompt
        request.systemPrompt?.let {
            messages.add(OpenAiMessage(role = "system", content = OpenAiContent.Text(it)))
        }

        // 对话历史
        for (msg in request.messages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.SYSTEM -> "system"
            }

            // 有附件时构建多模态消息
            val imageAttachments = msg.attachments.filter { it.isImage }
            val fileAttachments = msg.attachments.filter { !it.isImage }

            val content = when {
                imageAttachments.isNotEmpty() || fileAttachments.isNotEmpty() -> {
                    val parts = mutableListOf<ContentPart>()
                    // 图片附件：localPath 已在 ChatViewModel 中替换为 data URL
                    for (img in imageAttachments) {
                        parts.add(ContentPart.ImageUrlPart(img.localPath))
                    }
                    // 非图片文件：作为文本提示发送（大部分模型不支持非图片文件，告知用户文件名即可）
                    val fileHints = fileAttachments.mapNotNull { file ->
                        if (file.fileName.isNotBlank()) "[附件: ${file.fileName} (${formatFileSize(file.fileSize)})]" else null
                    }
                    // 文本内容 + 文件提示
                    val textContent = buildString {
                        if (msg.content.isNotBlank()) append(msg.content)
                        if (fileHints.isNotEmpty()) {
                            if (isNotBlank()) append("\n")
                            append(fileHints.joinToString("\n"))
                        }
                    }
                    if (textContent.isNotBlank()) {
                        parts.add(0, ContentPart.TextPart(textContent))
                    }
                    if (parts.size == 1 && parts[0] is ContentPart.TextPart) {
                        OpenAiContent.Text((parts[0] as ContentPart.TextPart).text)
                    } else {
                        OpenAiContent.MultiModal(parts)
                    }
                }
                else -> OpenAiContent.Text(msg.content)
            }

            messages.add(OpenAiMessage(role = role, content = content))
        }

        return messages
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private fun formatFileSize(bytes: Long): String = when {
            bytes <= 0 -> ""
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}
