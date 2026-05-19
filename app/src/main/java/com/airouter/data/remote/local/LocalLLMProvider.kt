package com.airouter.data.remote.local







import android.content.Context



import com.airouter.data.model.AiModel



import com.airouter.data.model.ChatChunk



import com.airouter.data.model.AttachmentType



import com.airouter.data.model.ChatRequest



import com.airouter.data.model.ChatResponse



import com.airouter.data.model.TokenUsage



import com.airouter.data.model.ModelCatalog



import com.airouter.debug.DebugLog



import com.airouter.domain.provider.AIProvider



import com.airouter.lib.AiChat



import com.airouter.lib.InferenceEngine



import kotlinx.coroutines.flow.Flow



import kotlinx.coroutines.flow.flow



import java.io.File







/**



 * 本地 LLM Provider - 使用 llama.cpp 进行本地推理



 * 支持多个已下载的 GGUF 模型，包括多模态模型



 */



class LocalLLMProvider(



    private val context: Context,



    private val selectedModelId: String? = null



) : AIProvider {







    override val providerId: String = "local"



    override val providerName: String = "Local LLM"







    private val engine: InferenceEngine = AiChat.getInferenceEngine(context)



    private var currentModelId: String? = null



    private var modelLoaded = false







    private fun getModelPathById(modelId: String): String? {



        val entry = ModelCatalog.findById(modelId) ?: return null



        val modelsDir = File(context.filesDir, "models")



        val file = File(modelsDir, entry.fileName)



        return if (file.exists()) file.absolutePath else null



    }







    private fun getMmprojPathById(modelId: String): String? {



        val entry = ModelCatalog.findById(modelId) ?: return null



        if (!entry.isMultimodal || entry.mmprojFileName.isEmpty()) return null



        val modelsDir = File(context.filesDir, "models")



        val file = File(modelsDir, entry.mmprojFileName)



        return if (file.exists()) file.absolutePath else null



    }







    private fun getDefaultDownloadedModel(): String? {



        val modelsDir = File(context.filesDir, "models")



        val downloaded = ModelCatalog.getDownloadedIds(modelsDir)



        return downloaded.firstNotNullOfOrNull { getModelPathById(it) }



    }







    private fun loadModelById(modelId: String?): Boolean {



        val modelsDir = File(context.filesDir, "models")



        val entry = modelId?.let { ModelCatalog.findById(it) }







        if (entry != null) {



            if (!ModelCatalog.isModelFileValid(modelsDir, entry)) {



                DebugLog.log("LocalLLM", "[ERROR] 模型文件校验失败: ${entry.displayName}")



                return false



            }



        }







        val path = modelId?.let { getModelPathById(it) } ?: getDefaultDownloadedModel()



            ?: return false







        val mmprojPath = modelId?.let { getMmprojPathById(it) }



        DebugLog.log("LocalLLM", ">>> loadModel: path=$path, mmproj=$mmprojPath")







        return try {



            engine.release()







            // 多模态模型需要同时加载 mmproj



            if (mmprojPath != null) {



                DebugLog.log("LocalLLM", "--- 加载多模态模型 (mmproj=$mmprojPath)...")



                modelLoaded = engine.loadMultimodalModel(path, mmprojPath)



            } else {



                DebugLog.log("LocalLLM", "--- 加载纯语言模型...")



                modelLoaded = engine.loadModel(path)



            }







            if (modelLoaded) {



                currentModelId = modelId ?: "default"



                DebugLog.log("LocalLLM", "<<< 模型加载成功")



            } else {



                DebugLog.log("LocalLLM", "[ERROR] 模型加载失败 (native返回false)")



            }



            modelLoaded



        } catch (e: Exception) {



            DebugLog.log("LocalLLM", "[ERROR] 模型加载异常: ${e.message}")



            modelLoaded = false



            false



        }



    }







    @Synchronized



    private fun ensureModelLoaded(modelId: String?): Boolean {
        DebugLog.log("LocalLLM", "ensureModelLoaded: instance=${this.hashCode()}, modelId=$modelId, currentModelId=$currentModelId, modelLoaded=$modelLoaded")
        // 如果已加载同一个模型，直接返回
        if (modelLoaded && currentModelId == (modelId ?: "default")) {
            DebugLog.log("LocalLLM", "--- 模型已加载，跳过重复加载 (modelId=$modelId)")
            return true
        }
        DebugLog.log("LocalLLM", ">>> 需要加载模型: modelId=$modelId, currentModelId=$currentModelId, modelLoaded=$modelLoaded")
        return loadModelById(modelId)
    }







    override suspend fun listModels(): List<AiModel> {



        return ModelCatalog.models.map { entry ->



            val modelsDir = File(context.filesDir, "models")



            val isDownloaded = ModelCatalog.isModelFileValid(modelsDir, entry)



            val suffix = if (entry.isMultimodal) "👁" else ""



            AiModel(



                modelId = entry.id,



                displayName = if (isDownloaded) "${entry.displayName} (本地)" else "${entry.displayName} (未下载)"



            )



        }



    }







    override fun chatStream(request: ChatRequest): Flow<ChatChunk> = flow {



        val modelId = request.modelId.removePrefix("local-").takeIf { 



            ModelCatalog.findById(it) != null 



        }



        



        if (!ensureModelLoaded(modelId)) {



            val hint = if (getDefaultDownloadedModel() != null) {



                "[ERROR] 模型加载失败，请重启应用后重试"



            } else {



                "[ERROR] 模型未下载，请在设置中下载模型"



            }



            emit(ChatChunk(content = hint))



            return@flow



        }







        engine.resetChat()







        val lastUserMsg = request.messages.lastOrNull { it.role.name.equals("USER", true) }



        val userMsg = lastUserMsg?.content ?: ""



        var imagePath = lastUserMsg?.attachments



            ?.firstOrNull { it.type == AttachmentType.IMAGE }



            ?.localPath







        android.util.Log.d("AIRouter-LLM", "chatStream: original imagePath=$imagePath")







        // 修复：如果 imagePath 是 Data URI (data:image/...;base64,...)，解码成临时文件



        if (imagePath != null && imagePath.startsWith("data:image")) {



            android.util.Log.d("AIRouter-LLM", "chatStream: Detected Data URI, decoding to temp file...")



                DebugLog.log("LocalLLM", "[图片] 检测到 Data URI，开始解码...")



            try {



                val tempFile = java.io.File(context.cacheDir, "temp_image_" + System.currentTimeMillis() + ".jpg")



                val base64Data = imagePath.substringAfter(",")



                val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)



                tempFile.writeBytes(imageBytes)



                imagePath = tempFile.absolutePath



                DebugLog.log("LocalLLM", "[图片] 解码完成: $imagePath (${tempFile.length()} bytes)")



            } catch (e: Exception) {



                DebugLog.log("LocalLLM", "[ERROR][图片] Data URI 解码失败: ${e.message}")



                imagePath = null



            }



        }







        if (userMsg.isEmpty() && imagePath == null) {



            emit(ChatChunk(content = "[ERROR] 没有找到用户消息"))



            return@flow



        }







        try {



            if (imagePath != null) {



                DebugLog.log("LocalLLM", ">>> [图片聊天] 开始推理: text='${userMsg.take(50)}...', imagePath=$imagePath")



                val imgStartTime = System.currentTimeMillis()



                var imgTokenCount = 0



                engine.sendUserPromptWithImage(userMsg, imagePath).collect { token ->



                    imgTokenCount++



                    emit(ChatChunk(content = token))



                }



                DebugLog.log("LocalLLM", "<<< [图片聊天] 推理完成: $imgTokenCount tokens, ${System.currentTimeMillis() - imgStartTime}ms")



            } else {



                DebugLog.log("LocalLLM", ">>> [文本聊天] 开始推理: '${userMsg.take(50)}...'")



                val txtStartTime = System.currentTimeMillis()



                var txtTokenCount = 0



                engine.sendUserPrompt(userMsg).collect { token ->



                    txtTokenCount++



                    emit(ChatChunk(content = token))



                }



                DebugLog.log("LocalLLM", "<<< [文本聊天] 推理完成: $txtTokenCount tokens, ${System.currentTimeMillis() - txtStartTime}ms")



            }



        } catch (e: Exception) {



            DebugLog.log("LocalLLM", "[ERROR] 推理异常: ${e.message}")



        }



    }
















    override suspend fun chat(request: ChatRequest): ChatResponse {



        val modelId = request.modelId.removePrefix("local-").takeIf { 



            ModelCatalog.findById(it) != null 



        }



        



        if (!ensureModelLoaded(modelId)) {



            return ChatResponse(



                content = if (getDefaultDownloadedModel() != null) {



                    "[ERROR] 模型加载失败，请重启应用后重试"



                } else {



                    "[ERROR] 模型未下载，请在设置中下载模型"



                },



                modelId = request.modelId



            )



        }







        return try {



            engine.resetChat()



            



            val lastUserMsg = request.messages.lastOrNull { it.role.name.equals("USER", true) }



            val userMsg = lastUserMsg?.content ?: ""



            var imagePath = lastUserMsg?.attachments



                ?.firstOrNull { it.type == AttachmentType.IMAGE }



                ?.localPath







            android.util.Log.d("AIRouter-LLM", "chat: original imagePath=$imagePath")







            // 修复：如果 imagePath 是 Data URI (data:image/...;base64,...)，解码成临时文件



            if (imagePath != null && imagePath.startsWith("data:image")) {



                android.util.Log.d("AIRouter-LLM", "chat: Detected Data URI, decoding to temp file...")



                try {



                    val tempFile = java.io.File(context.cacheDir, "temp_image_" + System.currentTimeMillis() + ".jpg")



                    val base64Data = imagePath.substringAfter(",")



                    val imageBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)



                    tempFile.writeBytes(imageBytes)



                    imagePath = tempFile.absolutePath



                    android.util.Log.d("AIRouter-LLM", "chat: Decoded Data URI to temp file: $imagePath")



                } catch (e: Exception) {



                    android.util.Log.e("AIRouter-LLM", "chat: Failed to decode Data URI", e)



                    imagePath = null



                }



            }







            if (userMsg.isEmpty() && imagePath == null) {



                return ChatResponse(content = "[ERROR] 没有找到用户消息", modelId = request.modelId)



            }







            val response = StringBuilder()



            if (imagePath != null) {



                android.util.Log.d("AIRouter-LLM", "chat: calling sendUserPromptWithImage(userMsg='${userMsg.take(30)}...', imagePath='$imagePath')")



                engine.sendUserPromptWithImage(userMsg, imagePath).collect { token ->



                    response.append(token)



                }



            } else {



                engine.sendUserPrompt(userMsg).collect { token ->



                    response.append(token)



                }



            }







            ChatResponse(



                content = response.toString(),



                modelId = request.modelId,



                usage = TokenUsage()



            )



        } catch (e: Exception) {



            ChatResponse(



                content = "[ERROR] 推理失败: ${e.message}",



                modelId = request.modelId



            )



        }



    }







    override suspend fun validateApiKey(): Boolean {



        return ensureModelLoaded(selectedModelId)



    }







    fun release() {



        engine.release()



        modelLoaded = false



        currentModelId = null



    }



}



