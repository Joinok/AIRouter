path = r"C:\Users\Administrator\.qclaw\workspace\AIRouter\app\src\main\java\com\airouter\data\remote\local\LocalLLMProvider.kt"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

# Find the ensureModelLoaded function
for i in range(len(lines)):
    if "@Synchronized" in lines[i] and i+1 < len(lines) and "private fun ensureModelLoaded" in lines[i+1]:
        print(f"Found ensureModelLoaded at line {i+1}")
        # Find the "if (modelLoaded && currentModelId" line
        for j in range(i, min(i+10, len(lines))):
            if "if (modelLoaded && currentModelId ==" in lines[j]:
                print(f"Found if statement at line {j+1}")
                # Insert debug log before return true
                # The pattern is: if (...) return true
                # We want: if (...) { DebugLog...; return true }
                
                # Find the exact line that has "return true"
                for k in range(j, min(j+3, len(lines))):
                    if "return true" in lines[k]:
                        print(f"Found 'return true' at line {k+1}")
                        # Get the indentation
                        indent = len(lines[k]) - len(lines[k].lstrip())
                        indent_str = " " * indent
                        
                        # Modify the if line to add {
                        old_if = lines[j]
                        if "{" not in old_if:
                            lines[j] = old_if.rstrip() + " {\n"
                        
                        # Insert DebugLog before return true
                        lines.insert(k, f"{indent_str}    DebugLog.log(\"LocalLLM\", \"--- 模型已加载，跳过重复加载 (modelId=$modelId)\")\n")
                        
                        # Add closing brace after return true
                        lines.insert(k+2, f"{indent_str}}}\n")
                        
                        print("[OK] Added skip log")
                        break
                break
        break

with open(path, "w", encoding="utf-8") as f:
    f.writelines(lines)

print("Done")
