# AIRouter

**AIRouter** 是一款 Android AI 聚合客户端，同时支持**本地 LLM 推理**与**在线 AI 提供商**，让你在手机上自由切换、智能路由 AI 对话。

## ✨ 功能特性

- 🔁 **本地 LLM 推理**：通过 llama.cpp (NDK) 在 Android 设备上直接运行 GGUF 大模型
- 🌐 **多种在线 AI 提供商**：OpenAI、Claude、Gemini、DeepSeek 等
- 📦 **模型管理**：内置 8 款主流模型，支持下载、暂停、恢复、删除
- ⏸️ **断点续传**：下载中断可恢复，进度条实时显示
- 🔄 **智能路由**：根据模型可用性自动选择最佳提供商
- 🎨 **现代 UI**：基于 Jetpack Compose 构建，Material 3 风格

## 📦 内置模型目录

| 模型 | 大小 | 内存需求 | 特点 |
|------|------|----------|------|
| Qwen2.5 3B | ~3.1GB | 6GB+ | 中文能力强，推荐 ⭐ |
| Qwen2.5 1.5B | ~1.8GB | 4GB+ | 更小更快 |
| DeepSeek-R1 1.5B | ~1.8GB | 4GB+ | 数学推理强 |
| Gemma 2 2B | ~2.6GB | 5GB+ | 多语言支持好 |
| Phi-3 Mini 3.8B | ~3.8GB | 6GB+ | 英文为主，推理强 |
| Yi 1.5 6B | ~6.3GB | 8GB+ | 中英双语 |
| Qwen2.5 7B | ~7.2GB | 10GB+ | 效果更佳 |
| Llama 3.1 8B | ~8.6GB | 12GB+ | 需梯子下载 |

模型下载源已适配国内网络（ModelScope 镜像 + hf-mirror.com）。

## 🛠 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose (Material 3)
- **架构**：MVVM + MVI
- **依赖注入**：Koin
- **数据库**：Room (SQLite)
- **网络**：OkHttp、ktor
- **本地推理**：llama.cpp（通过 NDK 编译为 `libllama-jni.so`）
- **异步**：Kotlin 协程 + Flow
- **序列化**：kotlinx.serialization

## 🏗 项目结构

```
AIRouter/
├── app/                 # 主应用模块
│   ├── di/             # 依赖注入模块
│   ├── domain/         # 领域层（UseCase、Provider 接口）
│   ├── data/           # 数据层（Repository、本地/远程数据源）
│   ├── ui/             # UI 层（Compose 页面）
│   └── AiRouterApp.kt  # Application 类
├── lib/                 # NDK 模块（llama.cpp JNI 封装）
│   ├── src/main/cpp/  # C++ 源码（ai_chat.cpp）
│   └── build.gradle.kts
└── build.gradle.kts     # 根构建文件
```

## 🚀 快速开始

### 构建 Debug APK

```bash
# 需要 JDK 21+、Android SDK、NDK 27
$env:JAVA_HOME="D:\Java\jdk-21.0.2"
$env:ANDROID_HOME="F:\sdk"
cd AIRouter
.\gradlew.bat assembleDebug --no-daemon
```

构建完成后 APK 位于：`app\build\outputs\apk\debug\app-debug.apk`

### 下载模型

1. 打开应用 → 底部「本地模型」
2. 选择模型 → 点击下载
3. 下载完成后即可在聊天中选择本地模型对话

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License