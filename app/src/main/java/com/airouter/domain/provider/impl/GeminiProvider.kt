package com.airouter.domain.provider.impl

import android.util.Log
import com.airouter.data.model.AiModel
import com.airouter.data.model.ChatChunk
import com.airouter.data.model.ChatRequest
import com.airouter.data.model.ChatResponse
import com.airouter.data.model.MessageRole
import com.airouter.data.model.Provider
import com.airouter.data.model.TokenUsage
import com.airouter.domain.provider.AIProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Google Gemini API Provider.
 * 实现 Google Gemini REST API (https://generativelanguage.googleapis.com/v1beta/models/...)
 */
class GeminiProvider(
    private val provider: Provider,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : AIProvider {

    override val providerId: String = provider.id
    override val providerName: String = provider.name

    private val baseUrl = provider.effectiveBaseUrl.trimEnd('/')
    private val apiKey = provider.apiKey

    override suspend fun listModels(): List<AiModel> {
        // Gemini 内置模型列表
        if (provider.supportedModels.isNotEmpty()) {
            return provider.supportedModels
        }

        // 尝试从 API 拉取可用模型
        return try {
            val request = Request.Builder()
                .url("$baseUrl/models?key=$apiKey")
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                // API 调用失败，返回内置列表
                getBuiltInModels()
            } else {
                val body = response.body?.string() ?: return getBuiltInModels()
                val modelsResponse = json.decodeFromString<GeminiModelsResponse>(body)
                modelsResponse.models?.map { model ->
                    val modelName = model.name.substringAfterLast("/")
                    AiModel(
                        modelId = modelName,
                        displayName = model.displayName ?: modelName,
                        contextLength = model.inputTokenLimit ?: 1048576,
                        supportsVision = model.supportedGenerationMethods?.contains("generateContent") ?: false
                    )
                } ?: getBuiltInModels()
            }
        } catch (e: Exception) {
            Log.w("GeminiProvider", "Failed to fetch models: ${e.message}")
            getBuiltInModels()
        }
    }

    private fun getBuiltInModels(): List<AiModel> {
        return listOf(
            AiModel(
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                contextLength = 1048576,
                supportsVision = true
            ),
            AiModel(
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                contextLength = 1048576,
                supportsVision = true
            ),
            AiModel(
                modelId = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                contextLength = 1048576,
                supportsVision = true
            ),
        )
    }

    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {
        val contents = buildGeminiContents(request)
        
        val geminiRequest = GeminiGenerateRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = request.temperature,
                topP = request.topP,
                maxOutputTokens = request.maxTokens
            ),
            systemInstruction = request.systemPrompt?.let {
                GeminiSystemInstruction(parts = listOf(GeminiPart(text = it)))
            }
        )

        val body = json.encodeToString(GeminiGenerateRequest.serializer(), geminiRequest)
        
        val url = "$baseUrl/models/${request.modelId}:streamGenerateContent?alt=sse&key=$apiKey"
        
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Gemini API error ${response.code}: $errorBody")
        }

        val inputStream = response.body?.byteStream() ?: throw Exception("Empty response")
        val reader = BufferedReader(InputStreamReader(inputStream))
        
        var inputTokens = 0
        var outputTokens = 0

        reader.useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data.isEmpty() || data == "[DONE]") {
                        return@forEach
                    }
                    
                    try {
                        val geminiResponse = json.decodeFromString<GeminiGenerateResponse>(data)
                        val text = geminiResponse.candidates?.firstOrNull()
                            ?.content?.parts?.firstOrNull()?.text ?: ""
                        
                        if (text.isNotEmpty()) {
                            emit(ChatChunk(content = text))
                        }
                        
                        // 提取 usage 信息（如果有）
                        geminiResponse.usageMetadata?.let { usage ->
                            inputTokens = usage.promptTokenCount ?: 0
                            outputTokens = usage.candidatesTokenCount ?: 0
                        }
                    } catch (e: Exception) {
                        Log.w("GeminiProvider", "Failed to parse SSE event: $data", e)
                    }
                }
            }
        }

        // 发送完成事件
        val usage = TokenUsage(
            promptTokens = inputTokens,
            completionTokens = outputTokens,
            totalTokens = inputTokens + outputTokens
        )
        emit(ChatChunk(content = "", finishReason = "stop", usage = usage))
    }

    override suspend fun chat(request: ChatRequest): ChatResponse {
        val contents = buildGeminiContents(request)
        
        val geminiRequest = GeminiGenerateRequest(
            contents = contents,
            generationConfig = GeminiGenerationConfig(
                temperature = request.temperature,
                topP = request.topP,
                maxOutputTokens = request.maxTokens
            ),
            systemInstruction = request.systemPrompt?.let {
                GeminiSystemInstruction(parts = listOf(GeminiPart(text = it)))
            }
        )

        val body = json.encodeToString(GeminiGenerateRequest.serializer(), geminiRequest)
        
        val url = "$baseUrl/models/${request.modelId}:generateContent?key=$apiKey"
        
        val httpRequest = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute()
        }

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw Exception("Gemini API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val geminiResponse = json.decodeFromString<GeminiGenerateResponse>(responseBody)
        
        val text = geminiResponse.candidates?.firstOrNull()
            ?.content?.parts?.firstOrNull()?.text ?: ""
        
        val usage = TokenUsage(
            promptTokens = geminiResponse.usageMetadata?.promptTokenCount ?: 0,
            completionTokens = geminiResponse.usageMetadata?.candidatesTokenCount ?: 0,
            totalTokens = (geminiResponse.usageMetadata?.promptTokenCount ?: 0) + 
                (geminiResponse.usageMetadata?.candidatesTokenCount ?: 0)
        )

        return ChatResponse(
            content = text,
            modelId = request.modelId,
            usage = usage
        )
    }

    override suspend fun validateApiKey(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/models?key=$apiKey")
                .get()
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }
            
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    private fun buildGeminiContents(request: ChatRequest): List<GeminiContent> {
        val contents = mutableListOf<GeminiContent>()

        for (msg in request.messages) {
            val role = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "model"
                MessageRole.SYSTEM -> "user"  // Gemini 把 system 单独处理
            }

            // 处理图片附件
            val imageAttachments = msg.attachments.filter { it.isImage }
            
            val parts = mutableListOf<GeminiPart>()
            
            // 添加文本
            if (msg.content.isNotBlank()) {
                parts.add(GeminiPart(text = msg.content))
            }
            
            // 添加图片
            for (img in imageAttachments) {
                // img.localPath 应该是 data URL (data:image/...;base64,...)
                val base64Data = img.localPath.substringAfter(",", "")
                val mimeType = img.localPath.substringAfter("data:", "")
                    .substringBefore(";base64", "image/jpeg")
                
                if (base64Data.isNotEmpty()) {
                    parts.add(
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = mimeType,
                                data = base64Data
                            )
                        )
                    )
                }
            }
            
            if (parts.isNotEmpty()) {
                contents.add(GeminiContent(role = role, parts = parts))
            }
        }

        return contents
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

// Gemini API 数据类
@Serializable
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiSystemInstruction? = null
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiSystemInstruction(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiContent(
    val role: String,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    val mimeType: String,
    val data: String  // base64 编码的图片数据
)

@Serializable
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null,
    val usageMetadata: GeminiUsageMetadata? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

@Serializable
data class GeminiUsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

// 模型列表响应
@Serializable
data class GeminiModelsResponse(
    val models: List<GeminiModel>? = null
)

@Serializable
data class GeminiModel(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val inputTokenLimit: Int? = null,
    val supportedGenerationMethods: List<String>? = null
)
