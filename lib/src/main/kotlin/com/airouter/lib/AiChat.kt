package com.airouter.lib

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AiChat 门面类 - 提供推理引擎的入口
 */
object AiChat {
    
    private var inferenceEngine: InferenceEngine? = null
    
    /**
     * 获取推理引擎实例（单例）
     */
    fun getInferenceEngine(context: Context): InferenceEngine {
        return inferenceEngine ?: createInferenceEngine(context).also {
            inferenceEngine = it
        }
    }
    
    /**
     * 创建推理引擎实例
     */
    private fun createInferenceEngine(context: Context): InferenceEngine {
        return object : InferenceEngine {
            private var modelLoaded = false
            private var modelPath: String? = null
            
            init {
                // 调用 native 初始化
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
            
            override fun resetChat() {
                nativeReset()
            }
            
            override fun sendUserPrompt(text: String): Flow<String> = flow {
                if (!modelLoaded) {
                    emit("[ERROR] 模型未加载，请先加载模型")
                    return@flow
                }
                
                // 调用 native 推理（简化版本：模拟流式输出）
                val response = nativeChat(text)
                emit(response)
            }
            
            override fun release() {
                nativeFree()
                modelLoaded = false
                modelPath = null
                inferenceEngine = null
            }
        }
    }
    
    // JNI 方法声明
    private external fun nativeInit(): Boolean
    private external fun nativeLoadModel(modelPath: String): Boolean
    private external fun nativeFree()
    private external fun nativeChat(input: String): String
    private external fun nativeReset()
    
    init {
        System.loadLibrary("llama-jni")
    }
}
