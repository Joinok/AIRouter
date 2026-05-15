// 占位文件，后续替换为真实的 llama.cpp 源码
// 这个文件仅用于让 CMake 配置能够通过编译

#include <jni.h>
#include <string>

// 空实现，仅用于编译通过
extern "C" {
    JNIEXPORT jstring JNICALL
    Java_com_airouter_lib_AiChat_nativeInit(JNIEnv* env, jobject /* this */) {
        std::string hello = "Dummy implementation - replace with real llama.cpp";
        return env->NewStringUTF(hello.c_str());
    }
}
