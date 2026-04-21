package com.airouter.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AiModel(
    val modelId: String,
    val displayName: String,
    val contextLength: Int = 8192,
    val inputPricePerMToken: Float? = null,
    val outputPricePerMToken: Float? = null,
    val supportsVision: Boolean = false,
    val supportsStreaming: Boolean = true,
    /** 该模型是否锁定 temperature（如 Kimi K2.5 只允许 1.0），非 null 时强制使用此值 */
    val fixedTemperature: Float? = null,
    /** 该模型是否锁定 top_p（如 Kimi K2.5 只允许 0.95），非 null 时强制使用此值 */
    val fixedTopP: Float? = null,
)
