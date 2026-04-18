package com.airouter.data.remote.sse

import android.util.Log
import com.airouter.data.model.TokenUsage
import com.airouter.data.remote.dto.OpenAiChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

class SseParser(
    private val client: OkHttpClient,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {

    fun streamChat(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): Flow<SseEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (key, value) -> addHeader(key, value) } }
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val factory = EventSources.createFactory(client)

        // 在回调线程上直接拼接内容，避免通过 channel 逐 chunk 传输导致 buffer 溢出丢失
        val contentBuilder = StringBuilder()
        var collectedUsage: TokenUsage? = null

        val eventSource = factory.newEventSource(request, object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                Log.d("SseParser", "SSE onOpen")
                trySend(SseEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data == "[DONE]") {
                    Log.d("SseParser", "SSE [DONE] received, total content length=${contentBuilder.length}")
                    // 发送最终完整内容
                    trySend(SseEvent.Chunk(contentBuilder.toString(), finishReason = "stop", usage = collectedUsage))
                    trySend(SseEvent.Done)
                    eventSource.cancel()
                    return
                }

                try {
                    val response = json.decodeFromString<OpenAiChatResponse>(data)
                    val choice = response.choices.firstOrNull()
                    if (choice == null) {
                        Log.w("SseParser", "SSE no choices in response: $data")
                        return
                    }
                    val delta = choice.delta?.content ?: ""
                    val finishReason = choice.finishReason
                    val usage = response.usage?.let {
                        TokenUsage(
                            promptTokens = it.promptTokens,
                            completionTokens = it.completionTokens,
                            totalTokens = it.totalTokens,
                        )
                    }
                    if (usage != null) collectedUsage = usage
                    if (delta.isNotEmpty()) contentBuilder.append(delta)
                    if (finishReason != null) {
                        Log.d("SseParser", "SSE finishReason=$finishReason")
                    }
                    // 发送累积的完整内容（而非单个 delta），消费端拿到的始终是完整文本
                    trySend(SseEvent.Chunk(contentBuilder.toString(), finishReason = null, usage = collectedUsage))
                } catch (e: Exception) {
                    Log.w("SseParser", "SSE parse error: ${e.message}, data=$data")
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                val body = response?.body?.string()
                Log.e("SseParser", "SSE onFailure: t=${t?.message}, code=${response?.code}, body=$body")
                val msg = buildString {
                    if (response != null) {
                        append("HTTP ${response.code}")
                        if (!body.isNullOrBlank()) {
                            append(": $body")
                        }
                    }
                    if (t != null) {
                        if (isNotEmpty()) append(" | ")
                        append(t.message)
                    }
                    if (isEmpty()) append("Unknown error")
                }
                trySend(SseEvent.Error(msg))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d("SseParser", "SSE onClosed (connection closed by server), content length=${contentBuilder.length}")
                // 关键：补发最终累积内容，防止 sample(50ms) 吞掉最后一个有效 chunk
                if (contentBuilder.isNotEmpty()) {
                    trySend(SseEvent.Chunk(contentBuilder.toString(), finishReason = "stop", usage = collectedUsage))
                }
                trySend(SseEvent.Done)
                close()
            }
        })

        awaitClose {
            eventSource.cancel()
        }
    }.buffer(capacity = 1024, onBufferOverflow = BufferOverflow.SUSPEND)
     .flowOn(Dispatchers.IO)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

sealed class SseEvent {
    data object Connected : SseEvent()
    data class Chunk(
        val content: String = "",
        val finishReason: String? = null,
        val usage: TokenUsage? = null,
    ) : SseEvent()
    data class Error(val message: String) : SseEvent()
    data object Done : SseEvent()
}
