package com.airouter.lib

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AiChat 门面类 - 提供推理引擎的入口
 */
object AiChat {
    
    private var inferenceEngine: InferenceEngine? = null
    
    fun getInferenceEngine(context: Context): InferenceEngine {
        return inferenceEngine ?: createInferenceEngine(context).also {
            inferenceEngine = it
        }
    }
    
    private fun createInferenceEngine(context: Context): InferenceEngine {
        return object : InferenceEngine {
            private var modelLoaded = false
            private var modelPath: String? = null
            
            init {
                nativeInit()
            }
            
            override fun loadModel(path: String): Boolean {
                return try {
                    modelPath = path
                    val result = nativeLoadModel(path)
                    modelLoaded = result
                    result
                } catch (e: Exception) {
                    false
                }
            }
            
            override fun loadMultimodalModel(modelPath: String, mmprojPath: String): Boolean {
                return try {
                    this.modelPath = modelPath
                    val result = nativeLoadMultimodalModel(modelPath, mmprojPath)
                    modelLoaded = result
                    result
                } catch (e: Exception) {
                    false
                }
            }
            
            override fun resetChat() {
                nativeReset()
            }
            
            override fun sendUserPrompt(text: String): Flow<String> = flow {
                if (!modelLoaded) {
                    emit("[ERROR] 模型未加载，请先加载模型")
                    return@flow
                }
                
                val response = nativeChat(text)
                emit(response)
            }
            
            override fun sendUserPromptWithImage(text: String, imagePath: String): Flow<String> = flow {
                if (!modelLoaded) {
                    emit("[ERROR] 模型未加载，请先加载模型")
                    return@flow
                }
                
                val response = nativeChatWithImage(text, imagePath)
                emit(response)
            }
            
            override fun release() {
                nativeFree()
                modelLoaded = false
                this.modelPath = null
                inferenceEngine = null
            }
        }
    }
    
    private external fun nativeInit(): Boolean
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeLoadMultimodalModel(modelPath: String, mmprojPath: String): Boolean
    private external fun nativeFree()
    private external fun nativeChat(input: String): String
    private external fun nativeChatWithImage(text: String, imagePath: String): String
    private external fun nativeReset()
    
    init {
        System.loadLibrary("llama-jni")
    }
}
