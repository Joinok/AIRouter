path = r"C:\Users\Administrator\.qclaw\workspace\AIRouter\app\src\main\java\com\airouter\data\remote\local\LocalLLMProvider.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Add log to ensureModelLoaded when skipping
old_ensure = '''    @Synchronized
    private fun ensureModelLoaded(modelId: String?): Boolean {
        // 如果已加载同一个模型，直接返回
        if (modelLoaded && currentModelId == (modelId ?: "default")) return true
        return loadModelById(modelId)
    }'''

new_ensure = '''    @Synchronized
    private fun ensureModelLoaded(modelId: String?): Boolean {
        // 如果已加载同一个模型，直接返回
        if (modelLoaded && currentModelId == (modelId ?: "default")) {
            DebugLog.log("LocalLLM", "--- 模型已加载，跳过重复加载 (modelId=$modelId)")
            return true
        }
        DebugLog.log("LocalLLM", ">>> 需要加载模型: modelId=$modelId, currentModelId=$currentModelId, modelLoaded=$modelLoaded")
        return loadModelById(modelId)
    }'''

if old_ensure in content:
    content = content.replace(old_ensure, new_ensure)
    print("[OK] ensureModelLoaded 日志已添加")
else:
    print("[SKIP] ensureModelLoaded 未找到（可能已有修改）")

# 2. Add response preview to completion logs
# 文本聊天完成
old_txt_done = 'DebugLog.log("LocalLLM", "<<< [文本聊天] 推理完成: $txtTokenCount tokens, ${System.currentTimeMillis() - txtStartTime}ms")'
new_txt_done = '''val txtPreview = responseBuilder.toString().take(100)
                DebugLog.log("LocalLLM", "<<< [文本聊天] 推理完成: $txtTokenCount tokens, ${System.currentTimeMillis() - txtStartTime}ms")
                DebugLog.log("LocalLLM", "--- 返回预览: $txtPreview...")'''

if old_txt_done in content:
    content = content.replace(old_txt_done, new_txt_done)
    print("[OK] 文本聊天返回预览已添加")
else:
    print("[SKIP] 文本聊天完成日志未找到")

# 图片聊天完成
old_img_done = 'DebugLog.log("LocalLLM", "<<< [图片聊天] 推理完成: $imgTokenCount tokens, ${System.currentTimeMillis() - imgStartTime}ms")'
new_img_done = '''val imgPreview = responseBuilder.toString().take(100)
                DebugLog.log("LocalLLM", "<<< [图片聊天] 推理完成: $imgTokenCount tokens, ${System.currentTimeMillis() - imgStartTime}ms")
                DebugLog.log("LocalLLM", "--- 返回预览: $imgPreview...")'''

if old_img_done in content:
    content = content.replace(old_img_done, new_img_done)
    print("[OK] 图片聊天返回预览已添加")
else:
    print("[SKIP] 图片聊天完成日志未找到")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("\nDone")
