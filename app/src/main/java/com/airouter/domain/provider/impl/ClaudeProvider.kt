package com.airouter.domain.provider.impl

import android.util.Log
import com.airouter.data.model.ChatMessage
import com.airouter.data.model.AiModel
import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import com.airouter.data.model.MessageRole
import com.airouter.data.model.Provider
import com.airouter.data.model.TokenUsage
import com.airouter.domain.provider.AIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Claude (Anthropic Messages API) Provider 实现
 * API 文档：https://docs.anthropic.com/en/api/messages
 *
 * 关键特点：
 * - Header: x-api-key（不是 Authorization: Bearer）
 * - system 是独立字段，不在 messages 里
 * - SSE 格式：event 类型有 message_start / content_block_delta / message_delta / message_stop
 * - content 可以是字符串，也可以是 [{type:"text",text:"..."}, {type:"image",source:{...}}]
 */
class ClaudeProvider(
    private val provider: Provider,
    private val client: OkHttpClient,
) : AIProvider {

    override val providerId: String get() = provider.id
    override val providerName: String get() = provider.name

    companion object {
        private const val TAG = "ClaudeProvider"
        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
        val apiKey = provider.apiKey

        val claudeMessages = buildClaudeMessages(request)
        val systemText = extractSystemText(request)

        val requestBody = ClaudeChatRequest(
            model = request.modelId,
            messages = claudeMessages,
            maxTokens = request.maxTokens ?: 4096,
            temperature = request.temperature,
            topP = request.topP,
            stream = true,
            system = if (systemText.isNotBlank())
                listOf(ClaudeSystemContent(text = systemText))
            else null
        )

        val bodyStr = JSON.encodeToString(ClaudeChatRequest.serializer(), requestBody)
        Log.d(TAG, "chatStream request: $bodyStr")

        val httpRequest = Request.Builder()
            .url("$baseUrl/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "HTTP ${response.code}"
            throw Exception("Claude API 错误: $err")
        }

        // 手动解析 Anthropic SSE 格式
        var inputTokens = 0
        var outputTokens = 0
        var currentEventType = ""

        val inputStream = response.body?.byteStream() ?: throw Exception("Empty response")
        val reader = BufferedReader(InputStreamReader(inputStream))

        reader.useLines { lines ->
            for (line in lines) {
                when {
                    line.startsWith("event:") -> {
                        currentEventType = line.substringAfter("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        val data = line.substringAfter("data:").trim()
                        if (data == "[DONE]") {
                            emit(ChatChunk(
                                content = "",
                                finishReason = "stop",
                                usage = TokenUsage(inputTokens, outputTokens, inputTokens + outputTokens)
                            ))
                            continue
                        }
                        try {
                            when (currentEventType) {
                                "message_start" -> {
                                    val event = JSON.decodeFromString<JsonObject>(data)
                                    val usage = event["message"]?.jsonObject?.get("usage")?.jsonObject
                                    inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.int ?: 0
                                    Log.d(TAG, "message_start: inputTokens=$inputTokens")
                                }
                                "content_block_delta" -> {
                                    val event = JSON.decodeFromString<JsonObject>(data)
                                    val delta = event["delta"]?.jsonObject
                                    val text = delta?.get("text")?.jsonPrimitive?.content
                                    if (!text.isNullOrEmpty()) {
                                        emit(ChatChunk(content = text))
                                    }
                                }
                                "message_delta" -> {
                                    val event = JSON.decodeFromString<JsonObject>(data)
                                    val usage = event["usage"]?.jsonObject
                                    outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.int ?: 0
                                    Log.d(TAG, "message_delta: outputTokens=$outputTokens")
                                }
                                "message_stop" -> {
                                    // 发送最终 chunk（带 usage）
                                    emit(ChatChunk(
                                        content = "",
                                        finishReason = "stop",
                                        usage = TokenUsage(inputTokens, outputTokens, inputTokens + outputTokens)
                                    ))
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "SSE parse error: ${e.message}, data=$data")
                        }
                    }
                }
            }
        }
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
        val apiKey = provider.apiKey

        val claudeMessages = buildClaudeMessages(request)
        val systemText = extractSystemText(request)

        val requestBody = ClaudeChatRequest(
            model = request.modelId,
            messages = claudeMessages,
            maxTokens = request.maxTokens ?: 4096,
            temperature = request.temperature,
            topP = request.topP,
            stream = false,
            system = if (systemText.isNotBlank())
                listOf(ClaudeSystemContent(text = systemText))
            else null
        )

        val bodyStr = JSON.encodeToString(ClaudeChatRequest.serializer(), requestBody)
        Log.d(TAG, "chat request: $bodyStr")

        val httpRequest = Request.Builder()
            .url("$baseUrl/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(bodyStr.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
        if (!response.isSuccessful) {
            val err = response.body?.string() ?: "HTTP ${response.code}"
            throw Exception("Claude API 错误: $err")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        Log.d(TAG, "chat response: ${responseBody.take(500)}")

        val claudeResponse = JSON.decodeFromString<ClaudeChatResponse>(responseBody)
        val contentText = claudeResponse.content
            .filter { it.type == "text" }
            .joinToString("") { it.text ?: "" }

        val usage = claudeResponse.usage
        return ChatResponse(
            content = contentText,
            usage = TokenUsage(
                promptTokens = usage?.inputTokens ?: 0,
                completionTokens = usage?.outputTokens ?: 0,
            )
        )
    }

    override suspend fun validateApiKey(): Boolean {
        return try {
            val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
            val apiKey = provider.apiKey
            val testRequest = ClaudeChatRequest(
                model = provider.supportedModels.firstOrNull()?.modelId ?: "claude-haiku-4-20250514",
                messages = listOf(ClaudeMessage(role = "user", content = JsonPrimitive("Hi"))),
                maxTokens = 1,
                stream = false
            )
            val body = JSON.encodeToString(ClaudeChatRequest.serializer(), testRequest)
            val httpRequest = Request.Builder()
                .url("$baseUrl/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
            response.isSuccessful || response.code == 400
        } catch (e: Exception) {
            Log.w(TAG, "validateApiKey failed: ${e.message}")
            false
        }
    }

    override suspend fun listModels(): List<AiModel> {
        // Claude 没有 /models 接口，返回内置列表
        return provider.supportedModels
    }

    /**
     * 将 ChatRequest 转换为 Claude API 的 messages 格式
     * content 可以是字符串或数组（多模态）
     */
    private fun buildClaudeMessages(request: ChatRequest): List<ClaudeMessage> {
        val result = mutableListOf<ClaudeMessage>()
        for (msg in request.messages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                else -> "user"
            }

            val hasImages = msg.attachments.any { it.isImage }
            val content = if (hasImages) {
                // 多模态：构建 JSON 数组
                buildJsonArray {
                    // 添加文本部分
                    if (msg.content.isNotBlank()) {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", msg.content)
                        })
                    }
                    // 添加图片部分
                    for (att in msg.attachments.filter { it.isImage }) {
                        val base64 = att.localPath.substringAfter(",", "")
                        val mime = att.localPath.substringAfter("data:", "")
                            .substringBefore(";base64", "image/jpeg")
                        if (base64.isNotEmpty()) {
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", mime)
                                    put("data", base64)
                                })
                            })
                        }
                    }
                }
            } else {
                JsonPrimitive(msg.content)
            }
            result.add(ClaudeMessage(role = role, content = content))
        }
        return result
    }

    /**
     * 提取 system 消息（Claude 用独立字段，不在 messages 里）
     */
    private fun extractSystemText(request: ChatRequest): String {
        return request.messages
            .filter { it.role == MessageRole.SYSTEM }
            .joinToString("\n") { it.content }
    }
}

// ─── 请求数据类 ───────────────────────────────────────────────

@Serializable
data class ClaudeChatRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    val maxTokens: Int,
    val temperature: Float? = null,
    val topP: Float? = null,
    @kotlinx.serialization.SerialName("stream")
    val stream: Boolean = false,
    val system: List<ClaudeSystemContent>? = null,
)

@Serializable
data class ClaudeSystemContent(
    val type: String = "text",
    val text: String,
)

@Serializable
data class ClaudeMessage(
    val role: String,
    // content 可以是字符串或数组，用 JsonElement 灵活表示
    val content: JsonElement,
)

// ─── 响应数据类 ───────────────────────────────────────────────

@Serializable
data class ClaudeChatResponse(
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val model: String? = null,
    val content: List<ClaudeContentBlock> = emptyList(),
    @kotlinx.serialization.SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: ClaudeUsage? = null,
)

@Serializable
data class ClaudeContentBlock(
    val type: String,
    val text: String? = null,
)

@Serializable
data class ClaudeUsage(
    @kotlinx.serialization.SerialName("input_tokens")
    val inputTokens: Int? = null,
    @kotlinx.serialization.SerialName("output_tokens")
    val outputTokens: Int? = null,
)
