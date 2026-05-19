path = r"C:\Users\Administrator\.qclaw\workspace\AIRouter\app\src\main\java\com\airouter\data\remote\local\LocalLLMProvider.kt"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Old pattern (with extra blank lines)
old_pattern = '''    private fun ensureModelLoaded(modelId: String?): Boolean {



        // 如果已加载同一个模型，直接返回



        if (modelLoaded && currentModelId == (modelId ?: "default")) return true



        return loadModelById(modelId)



    }'''

new_pattern = '''    private fun ensureModelLoaded(modelId: String?): Boolean {
        // 如果已加载同一个模型，直接返回
        if (modelLoaded && currentModelId == (modelId ?: "default")) {
            DebugLog.log("LocalLLM", "--- 模型已加载，跳过重复加载 (modelId=$modelId)")
            return true
        }
        DebugLog.log("LocalLLM", ">>> 需要加载模型: modelId=$modelId, currentModelId=$currentModelId, modelLoaded=$modelLoaded")
        return loadModelById(modelId)
    }'''

if old_pattern in content:
    content = content.replace(old_pattern, new_pattern)
    print("[OK] Pattern matched and replaced")
else:
    print("[FAIL] Pattern not found")
    # Try to show what's actually there
    idx = content.find("private fun ensureModelLoaded")
    if idx != -1:
        print("Actual content around ensureModelLoaded:")
        print(repr(content[idx:idx+500]))

with open(path, "w", encoding="utf-8") as f:
    f.write(content)

print("Done")
