path = r"C:\Users\Administrator\.qclaw\workspace\AIRouter\app\src\main\java\com\airouter\data\remote\local\LocalLLMProvider.kt"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# Find line 397 (0-indexed 396)
# Current content:
#     private fun ensureModelLoaded(modelId: String?): Boolean {
#         // 如果已加载同一个模型，直接返回
#         if (modelLoaded && currentModelId == (modelId ?: "default")) return true
#         return loadModelById(modelId)
#     }

# Target:
#     private fun ensureModelLoaded(modelId: String?): Boolean {
#         // 如果已加载同一个模型，直接返回
#         if (modelLoaded && currentModelId == (modelId ?: "default")) {
#             DebugLog.log("LocalLLM", "--- 模型已加载，跳过重复加载 (modelId=$modelId)")
#             return true
#         }
#         DebugLog.log("LocalLLM", ">>> 需要加载模型: modelId=$modelId, currentModelId=$currentModelId, modelLoaded=$modelLoaded")
#         return loadModelById(modelId)
#     }

# Find the if line
for i in range(396, min(402, len(lines))):
    if "if (modelLoaded && currentModelId ==" in lines[i]:
        print(f"Found if at line {i+1}: {repr(lines[i])}")
        # Get indentation (should be 8 spaces)
        indent = len(lines[i]) - len(lines[i].lstrip())
        indent_str = " " * indent
        
        # Replace the if line
        lines[i] = f'{indent_str}if (modelLoaded && currentModelId == (modelId ?: "default")) {{\n'
        
        # Insert debug log before return true (next line)
        lines.insert(i+1, f'{indent_str}    DebugLog.log("LocalLLM", "--- 模型已加载，跳过重复加载 (modelId=$modelId)")\n')
        
        # Find the return true line (should be i+2 now)
        # and add closing brace after it
        for j in range(i+2, min(i+5, len(lines))):
            if "return true" in lines[j]:
                lines.insert(j+1, f'{indent_str}}}\n')
                # Insert the "need to load" log before loadModelById
                for k in range(j+2, min(j+5, len(lines))):
                    if "return loadModelById" in lines[k]:
                        lines.insert(k, f'{indent_str}DebugLog.log("LocalLLM", ">>> 需要加载模型: modelId=$modelId, currentModelId=$currentModelId, modelLoaded=$modelLoaded")\n')
                        print("[OK] All logs added")
                        break
                break
        break

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("Done")
