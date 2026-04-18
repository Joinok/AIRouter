package com.airouter.data.local

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import java.util.UUID

/**
 * 附件本地文件存储管理。
 * 将用户选择的图片/文件复制到 app 内部目录，避免 base64 存入 SQLite 导致 CursorWindow 溢出。
 */
class AttachmentStorage(private val context: Context) {

    private val attachmentsDir: File
        get() {
            val dir = File(context.filesDir, "attachments")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    /**
     * 从 Uri 复制文件到本地存储，返回本地文件路径。
     */
    fun saveFromUri(uri: Uri): FileInfo? {
        return try {
            val cr = context.contentResolver
            val mimeType = cr.getType(uri) ?: "application/octet-stream"
            val fileName = cr.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                cursor.moveToFirst()
                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "file"
                val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                Triple(name, size, mimeType)
            } ?: Triple("file", 0L, mimeType)

            val ext = extensionFromMimeType(fileName.third)
            val localFileName = "${UUID.randomUUID()}.$ext"
            val localFile = File(attachmentsDir, localFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            FileInfo(
                localPath = localFile.absolutePath,
                fileName = fileName.first,
                fileSize = fileName.second,
                mimeType = fileName.third,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 读取本地文件并返回 base64 编码（用于发送 API 请求）。
     */
    fun readAsBase64(localPath: String): String {
        val file = File(localPath)
        if (!file.exists()) return ""
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * 读取本地文件为 bytes（用于 UI 展示）。
     */
    fun readBytes(localPath: String): ByteArray? {
        val file = File(localPath)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * 删除本地附件文件。
     */
    fun delete(localPath: String) {
        try {
            File(localPath).delete()
        } catch (_: Exception) {}
    }

    data class FileInfo(
        val localPath: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
    )

    companion object {
        private fun extensionFromMimeType(mimeType: String): String {
            return when {
                mimeType.startsWith("image/jpeg") -> "jpg"
                mimeType.startsWith("image/png") -> "png"
                mimeType.startsWith("image/gif") -> "gif"
                mimeType.startsWith("image/webp") -> "webp"
                mimeType.startsWith("image/") -> "bin"
                mimeType.contains("pdf") -> "pdf"
                mimeType.contains("text") -> "txt"
                else -> "bin"
            }
        }
    }
}
