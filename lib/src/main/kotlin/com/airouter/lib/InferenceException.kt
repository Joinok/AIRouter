package com.airouter.lib

/**
 * 推理引擎自定义异常
 */
open class InferenceException(
    message: String,
    cause: Throwable? = null,
    val errorCode: Int = 0
) : Exception(message, cause) {
    
    companion object {
        const val ERROR_MODEL_NOT_LOADED = 1001
        const val ERROR_MODEL_LOAD_FAILED = 1002
        const val ERROR_INFERENCE_FAILED = 1003
        const val ERROR_NATIVE_INIT_FAILED = 1004
    }
}

/**
 * 模型未加载异常
 */
class ModelNotLoadedException(message: String = "模型未加载") : 
    InferenceException(message, null, InferenceException.ERROR_MODEL_NOT_LOADED)

/**
 * 模型加载失败异常
 */
class ModelLoadFailedException(message: String = "模型加载失败") : 
    InferenceException(message, null, InferenceException.ERROR_MODEL_LOAD_FAILED)
