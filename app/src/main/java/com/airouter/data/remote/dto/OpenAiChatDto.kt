package com.airouter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class OpenAiChatRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val top_p: Float? = null,
    val stream: Boolean = false,
    val stream_options: StreamOptions? = null,
)

@Serializable
data class StreamOptions(
    @SerialName("include_usage")
    val includeUsage: Boolean = true,
)

/**
 * OpenAI 消息格式，content 支持多模态：
 * - 纯文本: content = "hello"
 * - 多模态: content = [{"type":"text","text":"..."},{"type":"image_url","image_url":{"url":"..."}}]
 */
@Serializable
data class OpenAiMessage(
    val role: String,
    @Serializable(with = OpenAiContentSerializer::class)
    val content: OpenAiContent = OpenAiContent.Text(""),
)

sealed class OpenAiContent {
    data class Text(val text: String) : OpenAiContent()
    data class MultiModal(val parts: List<ContentPart>) : OpenAiContent()
}

/** JSON 序列化：纯文本直接输出 String，多模态输出数组 */
object OpenAiContentSerializer : kotlinx.serialization.KSerializer<OpenAiContent> {
    private val delegate = kotlinx.serialization.json.JsonElement.serializer()
    override val descriptor = delegate.descriptor

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: OpenAiContent) {
        val element = when (value) {
            is OpenAiContent.Text -> kotlinx.serialization.json.JsonPrimitive(value.text)
            is OpenAiContent.MultiModal -> kotlinx.serialization.json.JsonArray(
                value.parts.map { part ->
                    when (part) {
                        is ContentPart.TextPart -> kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                            put("text", kotlinx.serialization.json.JsonPrimitive(part.text))
                        }
                        is ContentPart.ImageUrlPart -> kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("image_url"))
                            put("image_url", kotlinx.serialization.json.buildJsonObject {
                                put("url", kotlinx.serialization.json.JsonPrimitive(part.url))
                            })
                        }
                    }
                }
            )
        }
        encoder.encodeSerializableValue(delegate, element)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): OpenAiContent {
        val element = decoder.decodeSerializableValue(delegate)
        return when {
            element is kotlinx.serialization.json.JsonPrimitive -> OpenAiContent.Text(element.content)
            element is kotlinx.serialization.json.JsonArray -> {
                val parts = element.mapNotNull { partEl ->
                    val obj = partEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "text" -> ContentPart.TextPart(obj["text"]?.jsonPrimitive?.content ?: "")
                        "image_url" -> {
                            val imageUrlObj = obj["image_url"] as? kotlinx.serialization.json.JsonObject
                            ContentPart.ImageUrlPart(imageUrlObj?.get("url")?.jsonPrimitive?.content ?: "")
                        }
                        else -> null
                    }
                }
                OpenAiContent.MultiModal(parts)
            }
            else -> OpenAiContent.Text("")
        }
    }
}

sealed class ContentPart {
    data class TextPart(val text: String) : ContentPart()
    data class ImageUrlPart(val url: String) : ContentPart()
}

@Serializable
data class OpenAiChatResponse(
    val id: String? = null,
    val choices: List<OpenAiChoice>,
    val model: String? = null,
    val usage: OpenAiUsage? = null,
)

@Serializable
data class OpenAiChoice(
    val index: Int = 0,
    val message: OpenAiMessage? = null,
    val delta: OpenAiDelta? = null,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)

@Serializable
data class OpenAiDelta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
)

@Serializable
data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0,
)

@Serializable
data class OpenAiModelsResponse(
    val data: List<OpenAiModelInfo>? = null,
)

@Serializable
data class OpenAiModelInfo(
    val id: String,
    val owned_by: String? = null,
)
