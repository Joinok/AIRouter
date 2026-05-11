package com.airouter.data.model

import kotlinx.serialization.Serializable

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sessionId: String = "",
    val role: MessageRole = MessageRole.USER,
    val content: String = "",
    val modelId: String? = null,
    val providerId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenUsage: TokenUsage? = null,
    val isStreaming: Boolean = false,
    val isReasoning: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    /** 附件列表（本地文件路径），JSON 序列化存储 */
    val attachments: List<MessageAttachment> = emptyList(),
)

/**
 * 附件数据模型。
 * [localPath] 指向 app 内部存储的文件路径，不再将 base64 存入数据库。
 */
@Serializable
data class MessageAttachment(
    val type: AttachmentType = AttachmentType.IMAGE,
    /** 本地文件绝对路径，如 /data/data/com.airouter/files/attachments/xxx.jpg */
    val localPath: String = "",
    /** MIME 类型，如 image/jpeg, image/png, application/pdf */
    val mimeType: String = "",
    /** 文件名 */
    val fileName: String = "",
    /** 文件大小（字节） */
    val fileSize: Long = 0,
) {
    /** 是否为图片 */
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

enum class AttachmentType {
    IMAGE, FILE
}
