package com.airouter.lib

import kotlinx.coroutines.flow.Flow

/**
 * 推理引擎接口
 */
interface InferenceEngine {
    
    /**
     * 加载模型
     * @param path 模型文件路径
     * @return 是否加载成功
     */
    fun loadModel(path: String): Boolean
    
    /**
     * 加载多模态模型（LLM + 视觉编码器）
     * @param modelPath LLM 模型文件路径
     * @param mmprojPath 视觉编码器文件路径
     * @return 是否加载成功
     */
    fun loadMultimodalModel(modelPath: String, mmprojPath: String): Boolean
    
    /**
     * 重置对话状态
     */
    fun resetChat()
    
    /**
     * 发送用户提示并获取流式回复
     * @param text 用户输入文本
     * @return 流式 token 输出
     */
    fun sendUserPrompt(text: String): Flow<String>
    
    /**
     * 发送带图片的用户提示并获取流式回复
     * @param text 用户输入文本
     * @param imagePath 图片文件路径
     * @return 流式 token 输出
     */
    fun sendUserPromptWithImage(text: String, imagePath: String): Flow<String>
    
    /**
     * 释放资源
     */
    fun release()
}
