#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define TAG "AiChat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// 推理状态结构体（简化版本）
struct InferenceState {
    // 后续添加真实的 llama.cpp 上下文
    bool modelLoaded = false;
    std::string modelPath;
    
    // 模拟推理
    int tokenCount = 0;
};

// 全局状态
InferenceState g_inferenceState;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_airouter_lib_AiChat_nativeInit(JNIEnv* env, jobject /* this */) {
    LOGI("nativeInit called");
    // 简化实现
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_airouter_lib_AiChat_nativeLoadModel(JNIEnv* env, jobject /* this */, jstring modelPath) {
    LOGI("nativeLoadModel called");
    
    const char* pathStr = env->GetStringUTFChars(modelPath, nullptr);
    g_inferenceState.modelPath = pathStr;
    env->ReleaseStringUTFChars(modelPath, pathStr);
    
    // 简化实现：检查文件是否存在
    FILE* file = fopen(g_inferenceState.modelPath.c_str(), "rb");
    if (file != nullptr) {
        fclose(file);
        g_inferenceState.modelLoaded = true;
        LOGI("Model loaded successfully (simulation)");
        return JNI_TRUE;
    } else {
        LOGE("Failed to load model: %s", g_inferenceState.modelPath.c_str());
        return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_com_airouter_lib_AiChat_nativeFree(JNIEnv* env, jobject /* this */) {
    LOGI("nativeFree called");
    g_inferenceState.modelLoaded = false;
    g_inferenceState.modelPath.clear();
    g_inferenceState.tokenCount = 0;
}

JNIEXPORT jstring JNICALL
Java_com_airouter_lib_AiChat_nativeChat(JNIEnv* env, jobject /* this */, jstring input) {
    LOGI("nativeChat called");
    
    if (!g_inferenceState.modelLoaded) {
        return env->NewStringUTF("[ERROR] Model not loaded");
    }
    
    // 简化实现：模拟生成回复
    const char* inputStr = env->GetStringUTFChars(input, nullptr);
    std::string inputText(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);
    
    // 模拟 token 生成
    std::string response = "This is a simulated response to: " + inputText;
    g_inferenceState.tokenCount++;
    
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_airouter_lib_AiChat_nativeReset(JNIEnv* env, jobject /* this */) {
    LOGI("nativeReset called");
    g_inferenceState.tokenCount = 0;
    // 后续添加真实的状态重置
}

} // extern "C"
