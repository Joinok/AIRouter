package com.airouter.data.remote.sse

import android.util.Log
import com.airouter.data.model.TokenUsage
import com.airouter.debug.DebugLog
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
        // Kimi K2.6 等推理模型会先发送 reasoning_content，然后发送 content
        // 我们先缓存 reasoning_content，等 content 开始有值时才显示最终答案
        val reasoningBuilder = StringBuilder()

        val eventSource = factory.newEventSource(request, object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                val msg = "onOpen: HTTP ${response.code} ${response.message}"
                Log.d("SseParser", "SSE $msg")
                DebugLog.log("SSE", "● $msg")
                trySend(SseEvent.Connected)
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                Log.d("SseParser", "SSE onEvent: type=$type, data(len=${data.length})=${data.take(200)}")
                if (data == "[DONE]") {
                    Log.d("SseParser", "SSE [DONE] received, content length=${contentBuilder.length}, reasoning length=${reasoningBuilder.length}")
                    val finalContent = if (contentBuilder.isNotEmpty()) contentBuilder.toString() else reasoningBuilder.toString()
                    val displayContent = if (finalContent.length > 300) finalContent.take(300) + "..." else finalContent
                    DebugLog.log("SSE", "✓ [DONE] content(${finalContent.length}) = \"${displayContent}\"")
                    val isFinalReasoning = contentBuilder.isEmpty() && reasoningBuilder.isNotEmpty()
                    // 先发送最终内容（如果还没发的话），再发送 Done
                    if (finalContent.isNotEmpty()) {
                        trySend(SseEvent.Chunk(finalContent, isReasoning = isFinalReasoning, finishReason = "stop", usage = collectedUsage))
                    }
                    trySend(SseEvent.Done)
                    eventSource.cancel()
                    close()  // 关闭 channel，让 flow 结束，触发 ChatViewModel 的 finally
                    return
                }

                try {
                    // 记录原始 JSON 用于调试 K2.6 等模型
                    Log.d("SseParser", "SSE raw (len=${data.length}): ${data.take(300)}")
                    val response = json.decodeFromString<OpenAiChatResponse>(data)
                    val choice = response.choices.firstOrNull()
                    if (choice == null) {
                        Log.w("SseParser", "SSE no choices in response: $data")
                        return
                    }
                    val delta = choice.delta
                    val deltaContent = delta?.content ?: ""
                    val deltaReasoning = delta?.reasoningContent ?: ""
                    // 调试：打印 delta 的所有非空字段
                    Log.d("SseParser", "delta fields: role=${delta?.role}, content='${deltaContent.take(50)}', reasoning_content='${deltaReasoning.take(80)}', finishReason=${choice.finishReason}")

                    if (deltaReasoning.isNotEmpty()) {
                        reasoningBuilder.append(deltaReasoning)
        // DebugLog.log("SSE", "🧠 reasoning +${deltaReasoning.length} → total=${reasoningBuilder.length}")
                        Log.d("SseParser", "reasoning emitted: len=${reasoningBuilder.length}, content='${reasoningBuilder.toString().take(50)}'")
                        trySend(SseEvent.Chunk(reasoningBuilder.toString(), isReasoning = true, finishReason = null, usage = collectedUsage))
                    }

                    if (deltaContent.isNotEmpty()) {
                        if (contentBuilder.isEmpty()) {
                            reasoningBuilder.clear()
                        }
                        contentBuilder.append(deltaContent)
        // DebugLog.log("SSE", "📝 content +${deltaContent.length} → total=${contentBuilder.length}")
                        Log.d("SseParser", "content emitted: len=${contentBuilder.length}")
                        trySend(SseEvent.Chunk(contentBuilder.toString(), isReasoning = false, finishReason = null, usage = collectedUsage))
                    }
                    val usage = response.usage?.let {
                        TokenUsage(
                            promptTokens = it.promptTokens,
                            completionTokens = it.completionTokens,
                            totalTokens = it.totalTokens,
                        )
                    }
                    if (usage != null) collectedUsage = usage
                } catch (e: Exception) {
                    Log.w("SseParser", "SSE parse error: ${e.message}, data=$data")
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                // 注意：不要读取 response.body，SSE 场景下 body 可能已被关闭
                val errMsg = "onFailure: HTTP ${response?.code} | ${t?.message}"
                Log.e("SseParser", "SSE $errMsg")
                DebugLog.log("SSE", "✗ $errMsg")
                trySend(SseEvent.Error(buildErrorMessage(response, t)))
                eventSource.cancel()
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                Log.d("SseParser", "SSE onClosed, content length=${contentBuilder.length}")
                // Kimi K2.6 等模型没有 [DONE]，连接直接关闭
                // 此时 contentBuilder 可能还有内容（reasoning_content 未发送完的情况极少，
                // 更常见的是 content 已通过 [DONE] 发完，contentBuilder 已清空）
                // 为避免双次发送，只有 contentBuilder 非空时才发（说明 [DONE] 从未触发）
                if (contentBuilder.isNotEmpty()) {
                    trySend(SseEvent.Chunk(contentBuilder.toString(), isReasoning = false, finishReason = "stop", usage = collectedUsage))
                }
                trySend(SseEvent.Done)
                close()  // 关闭 channel，让 flow 结束
            }
        })

        awaitClose {
            Log.d("SseParser", "SSE awaitClose: cancelling event source")
            eventSource.cancel()
        }
    }.buffer(capacity = 1024, onBufferOverflow = BufferOverflow.SUSPEND)
     .flowOn(Dispatchers.IO)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
    private fun buildErrorMessage(response: okhttp3.Response?, t: Throwable?): String {
        return buildString {
            if (response != null) {
                append("HTTP ${response.code}")
                // 注意：不要在这里读取 response.body，因为 SSE 流结束后 body 已被关闭
                // 如果需要响应体内容，应该在 onFailure 回调中提前保存
            }
            if (t != null) {
                if (isNotEmpty()) append(" | ")
                append(t.message)
            }
            if (isEmpty()) append("Unknown error")
        }
    }
}

sealed class SseEvent {
    data object Connected : SseEvent()
    data class Chunk(
        val content: String = "",
        val isReasoning: Boolean = false,
        val finishReason: String? = null,
        val usage: TokenUsage? = null,
    ) : SseEvent()
    data class Error(val message: String) : SseEvent()
    data object Done : SseEvent()
}
