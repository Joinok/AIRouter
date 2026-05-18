package com.airouter.data.model

/**
 * 本地模型目录
 * 所有支持的 GGUF 模型列表，下载源统一使用魔搭社区（modelscope.cn）
 */
object ModelCatalog {

    /**
     * 模型条目
     */
    data class ModelEntry(
        val id: String,              // 唯一标识，如 "qwen2.5-3b"
        val displayName: String,     // 显示名称，如 "Qwen2.5 3B"
        val description: String,     // 模型描述
        val sizeLabel: String,       // 文件大小标签，如 "约 3.1GB"
        val fileName: String,         // 下载后的文件名
        val downloadUrl: String,     // GGUF 下载地址（魔搭社区镜像）
        val minRam: String,          // 最低内存需求
        val quant: String,           // 量化方式，如 "Q8_0"
        val recommended: Boolean = false,  // 是否推荐
        val expectedSizeBytes: Long = 0,   // 预期文件大小（字节），用于校验下载完整性
        val isMultimodal: Boolean = false, // 是否为多模态模型
        val mmprojFileName: String = "",   // 视觉编码器文件名（多模态模型）
        val mmprojDownloadUrl: String = "",// 视觉编码器下载地址（多模态模型）
        val mmprojExpectedSizeBytes: Long = 0 // 视觉编码器预期大小
    )

    /**
     * 支持的模型列表
     */
    val models = listOf(
        // ---- 纯文本模型 ----
        ModelEntry(
            id = "qwen2.5-3b",
            displayName = "Qwen2.5 3B",
            description = "通义千问 3B 量化版，中文能力强，适合日常对话",
            sizeLabel = "约 3.1GB",
            fileName = "qwen25_3b.gguf",
            downloadUrl = "https://www.modelscope.cn/models/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/master/qwen2.5-3b-instruct-q8_0.gguf",
            minRam = "6GB+",
            quant = "Q8_0",
            recommended = true,
            expectedSizeBytes = 3_100_000_000L
        ),
        ModelEntry(
            id = "qwen2.5-1.5b",
            displayName = "Qwen2.5 1.5B",
            description = "通义千问 1.5B 量化版，更小更快，适合轻量场景",
            sizeLabel = "约 1.8GB",
            fileName = "qwen25_1.5b.gguf",
            downloadUrl = "https://www.modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q8_0.gguf",
            minRam = "4GB+",
            quant = "Q8_0",
            expectedSizeBytes = 1_800_000_000L
        ),
        ModelEntry(
            id = "gemma-2-2b",
            displayName = "Gemma 2 2B",
            description = "Google Gemma 2B 量化版，多语言支持好",
            sizeLabel = "约 2.6GB",
            fileName = "gemma2_2b.gguf",
            downloadUrl = "https://hf-mirror.com/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q8_0.gguf",
            minRam = "5GB+",
            quant = "Q8_0",
            expectedSizeBytes = 2_600_000_000L
        ),
        ModelEntry(
            id = "phi-3-mini",
            displayName = "Phi-3 Mini 3.8B",
            description = "微软 Phi-3 Mini，推理能力强，英文为主",
            sizeLabel = "约 3.8GB",
            fileName = "phi3_mini.gguf",
            downloadUrl = "https://hf-mirror.com/bartowski/Phi-3-mini-4k-instruct-GGUF/resolve/main/Phi-3-mini-4k-instruct-Q8_0.gguf",
            minRam = "6GB+",
            quant = "Q8_0",
            expectedSizeBytes = 3_800_000_000L
        ),
        ModelEntry(
            id = "deepseek-r1-1.5b",
            displayName = "DeepSeek-R1 1.5B",
            description = "深度求索 1.5B 蒸馏版，数学推理强",
            sizeLabel = "约 1.8GB",
            fileName = "deepseek_r1_1.5b.gguf",
            downloadUrl = "https://hf-mirror.com/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q8_0.gguf",
            minRam = "4GB+",
            quant = "Q8_0",
            expectedSizeBytes = 1_800_000_000L
        ),
        ModelEntry(
            id = "yi-1.5-6b",
            displayName = "Yi 1.5 6B",
            description = "零一万物 Yi-1.5 6B，中英双语支持好",
            sizeLabel = "约 6.3GB",
            fileName = "yi15_6b.gguf",
            downloadUrl = "https://hf-mirror.com/bartowski/Yi-1.5-6B-Chat-GGUF/resolve/main/Yi-1.5-6B-Chat-Q8_0.gguf",
            minRam = "8GB+",
            quant = "Q8_0",
            expectedSizeBytes = 6_300_000_000L
        ),
        ModelEntry(
            id = "qwen2.5-7b",
            displayName = "Qwen2.5 7B",
            description = "通义千问 7B 量化版，效果更好但需要更多内存",
            sizeLabel = "约 7.2GB",
            fileName = "qwen25_7b.gguf",
            downloadUrl = "https://www.modelscope.cn/models/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/master/qwen2.5-7b-instruct-q8_0.gguf",
            minRam = "10GB+",
            quant = "Q8_0",
            expectedSizeBytes = 7_200_000_000L
        ),
        ModelEntry(
            id = "llama3.1-8b",
            displayName = "Llama 3.1 8B",
            description = "Meta Llama 3.1 8B，英文强，需梯子下载",
            sizeLabel = "约 8.6GB",
            fileName = "llama31_8b.gguf",
            downloadUrl = "https://huggingface.co/bartowski/Meta-Llama-3.1-8B-Instruct-GGUF/resolve/main/Meta-Llama-3.1-8B-Instruct-Q8_0.gguf",
            minRam = "12GB+",
            quant = "Q8_0",
            expectedSizeBytes = 8_600_000_000L
        ),

        // ---- 多模态模型（支持图像理解 + OCR） ----
        ModelEntry(
            id = "minicpm-v-4.6",
            displayName = "MiniCPM-V 4.6 👁",
            description = "面壁智能 1.3B 端侧多模态模型，支持图像理解/OCR/视频，专为手机优化",
            sizeLabel = "约 505MB",
            fileName = "minicpm_v4.6-q4km.gguf",
            downloadUrl = "https://hf-mirror.com/openbmb/MiniCPM-V-4.6-gguf/resolve/main/MiniCPM-V-4_6-Q4_K_M.gguf",
            minRam = "6GB+",
            quant = "Q4_K_M",
            recommended = true,
            expectedSizeBytes = 529_000_000L,
            isMultimodal = true,
            mmprojFileName = "minicpm_v4.6-mmproj-f16.gguf",
            mmprojDownloadUrl = "https://hf-mirror.com/openbmb/MiniCPM-V-4.6-gguf/resolve/main/mmproj-model-f16.gguf",
            mmprojExpectedSizeBytes = 1_108_000_000L
        ),
        ModelEntry(
            id = "qwen2.5-vl-3b",
            displayName = "Qwen2.5-VL 3B 👁",
            description = "通义千问视觉 3B，支持图像理解/OCR/文档解析，性价比高",
            sizeLabel = "约 3GB",
            fileName = "qwen25_vl_3b-q4km.gguf",
            downloadUrl = "https://hf-mirror.com/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",
            minRam = "6GB+",
            quant = "Q4_K_M",
            expectedSizeBytes = 2_000_000_000L,
            isMultimodal = true,
            mmprojFileName = "qwen25_vl_3b-mmproj-q8_0.gguf",
            mmprojDownloadUrl = "https://hf-mirror.com/ggml-org/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/mmproj-Qwen2.5-VL-3B-Instruct-Q8_0.gguf",
            mmprojExpectedSizeBytes = 1_000_000_000L
        ),
        ModelEntry(
            id = "qwen2.5-vl-7b",
            displayName = "Qwen2.5-VL 7B 👁",
            description = "通义千问视觉 7B，视觉能力强，适合高端手机",
            sizeLabel = "约 5GB",
            fileName = "qwen25_vl_7b-q4km.gguf",
            downloadUrl = "https://hf-mirror.com/ggml-org/Qwen2.5-VL-7B-Instruct-GGUF/resolve/main/Qwen2.5-VL-7B-Instruct-Q4_K_M.gguf",
            minRam = "8GB+",
            quant = "Q4_K_M",
            expectedSizeBytes = 4_500_000_000L,
            isMultimodal = true,
            mmprojFileName = "qwen25_vl_7b-mmproj-q8_0.gguf",
            mmprojDownloadUrl = "https://hf-mirror.com/ggml-org/Qwen2.5-VL-7B-Instruct-GGUF/resolve/main/mmproj-Qwen2.5-VL-7B-Instruct-Q8_0.gguf",
            mmprojExpectedSizeBytes = 1_700_000_000L
        )
    )

    /**
     * 根据 ID 查找模型条目
     */
    fun findById(id: String): ModelEntry? = models.find { it.id == id }

    /**
     * 获取所有已下载的模型 ID 列表
     * (由调用方传入 File 对象判断是否存在)
     */
    fun getDownloadedIds(modelsDir: java.io.File): List<String> {
        return models.filter { model ->
            isModelFileValid(modelsDir, model)
        }.map { it.id }
    }

    /**
     * 检查模型文件是否完整（文件存在且大小 >= 预期大小的 90%）
     * 多模态模型还需检查 mmproj 文件
     */
    fun isModelFileValid(modelsDir: java.io.File, model: ModelEntry): Boolean {
        val file = java.io.File(modelsDir, model.fileName)
        if (!file.exists()) return false
        // 如果没有设置预期大小，仅检查文件存在且非空
        if (model.expectedSizeBytes <= 0) {
            if (file.length() <= 0) return false
        } else {
            // 文件大小至少达到预期大小的 90% 才算完整
            if (file.length() < model.expectedSizeBytes * 0.9) return false
        }
        // 多模态模型的 mmproj 暂不强制要求（当前仅文本推理）
        // mmproj 下载完整性由下载管理器单独追踪
        return true
    }
}
