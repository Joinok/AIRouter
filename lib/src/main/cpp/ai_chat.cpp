#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <android/log.h>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"
#include "mtmd.h"

#define TAG "AiChat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// ---- Configuration ----
constexpr int   N_THREADS_MIN        = 2;
constexpr int   N_THREADS_MAX        = 4;
constexpr int   N_THREADS_HEADROOM   = 2;
constexpr int   DEFAULT_CONTEXT_SIZE = 2048;
constexpr int   BATCH_SIZE           = 512;
constexpr int   OVERFLOW_HEADROOM    = 4;
constexpr float DEFAULT_TEMP         = 0.6f;

// ---- Global state ----
static llama_model               * g_model   = nullptr;
static llama_context             * g_context = nullptr;
static llama_batch                  g_batch = llama_batch_init(0, 0, 0);
static common_chat_templates_ptr    g_chat_templates;
static common_sampler             * g_sampler = nullptr;
static std::vector<common_chat_msg> g_chat_msgs;
static llama_pos                    g_system_pos   = 0;
static llama_pos                    g_current_pos  = 0;

// ---- Multimodal state ----
static mtmd_context               * g_mtmd_ctx = nullptr;  // mmproj context for vision

// ---- Helpers ----
static int get_n_threads() {
    int n = (int)sysconf(_SC_NPROCESSORS_ONLN) - N_THREADS_HEADROOM;
    return std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, n));
}

static void reset_chat() {
    g_chat_msgs.clear();
    g_system_pos  = 0;
    g_current_pos = 0;
    if (g_context) {
        llama_memory_clear(llama_get_memory(g_context), false);
    }
}

static void free_resources() {
    reset_chat();
    if (g_mtmd_ctx) { mtmd_free(g_mtmd_ctx); g_mtmd_ctx = nullptr; }
    if (g_sampler) { common_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_templates.reset();
    if (g_batch.n_tokens > 0 || g_batch.pos != nullptr) {
        llama_batch_free(g_batch);
    }
    g_batch = llama_batch_init(0, 0, 0);  // reset to safe empty state
    if (g_context) { llama_free(g_context); g_context = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

static bool init_context_and_sampler() {
    int n_ctx = DEFAULT_CONTEXT_SIZE;
    int trained = llama_model_n_ctx_train(g_model);
    if (n_ctx > trained) {
        LOGW("Model trained with ctx=%d, capping to %d", trained, trained);
        n_ctx = trained;
    }

    int n_threads = get_n_threads();
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx          = n_ctx;
    cparams.n_batch        = BATCH_SIZE;
    cparams.n_ubatch       = BATCH_SIZE;
    cparams.n_threads      = n_threads;
    cparams.n_threads_batch = n_threads;

    g_context = llama_init_from_model(g_model, cparams);
    if (!g_context) {
        LOGE("Failed to create context");
        return false;
    }

    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");

    common_params_sampling sparams;
    sparams.temp = DEFAULT_TEMP;
    g_sampler = common_sampler_init(g_model, sparams);

    reset_chat();
    LOGI("Context initialized, ctx=%d, threads=%d", n_ctx, n_threads);
    return true;
}

// ---- JNI ----

extern "C" JNIEXPORT jboolean JNICALL
Java_com_airouter_lib_AiChat_nativeInit(JNIEnv * /*env*/, jobject /*this*/) {
    llama_log_set([](ggml_log_level level, const char * text, void * /*user*/) {
        (void)level;
        __android_log_print(ANDROID_LOG_INFO, TAG, "%s", text);
    }, nullptr);
    llama_backend_init();
    LOGI("llama.cpp backend initialized");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_airouter_lib_AiChat_nativeLoadModel(JNIEnv *env, jobject /*this*/, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model: %s", path);

    free_resources();

    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    if (!init_context_and_sampler()) {
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_airouter_lib_AiChat_nativeLoadMultimodalModel(JNIEnv *env, jobject /*this*/, jstring modelPath, jstring mmprojPath) {
    const char *model_path = env->GetStringUTFChars(modelPath, nullptr);
    const char *mmproj_path = env->GetStringUTFChars(mmprojPath, nullptr);
    LOGI("Loading multimodal model: %s (mmproj: %s)", model_path, mmproj_path);

    free_resources();

    // Load the LLM model
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(model_path, mparams);
    
    if (!g_model) {
        LOGE("Failed to load LLM model");
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }

    // Initialize context and sampler
    if (!init_context_and_sampler()) {
        llama_model_free(g_model);
        g_model = nullptr;
        env->ReleaseStringUTFChars(modelPath, model_path);
        env->ReleaseStringUTFChars(mmprojPath, mmproj_path);
        return JNI_FALSE;
    }

    // Load mmproj for vision support
    mtmd_context_params mtmd_params = mtmd_context_params_default();
    mtmd_params.warmup = false;  // skip warmup on mobile for faster init
    
    g_mtmd_ctx = mtmd_init_from_file(mmproj_path, mtmd_params);
    
    env->ReleaseStringUTFChars(modelPath, model_path);
    env->ReleaseStringUTFChars(mmprojPath, mmproj_path);

    if (!g_mtmd_ctx) {
        LOGW("Failed to load mmproj, continuing text-only mode");
        // Still return true - app works in text-only mode
    } else {
        LOGI("Multimodal model loaded successfully (vision + text)");
    }

    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_airouter_lib_AiChat_nativeChat(JNIEnv *env, jobject /*this*/, jstring input) {
    if (!g_model || !g_context || !g_sampler) {
        return env->NewStringUTF("[ERROR] Model not loaded");
    }

    // 如果是新一轮对话的开始，先清理 KV cache
    if (g_chat_msgs.empty()) {
        reset_chat();
    }

    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    std::string user_msg(inputStr);
    env->ReleaseStringUTFChars(input, inputStr);

    // Add system prompt on first message
    if (g_chat_msgs.empty()) {
        std::string sys_content = "You are a helpful assistant.";
        common_chat_msg sys_msg;
        sys_msg.role = "system";
        sys_msg.content = sys_content;

        bool has_template = common_chat_templates_was_explicit(g_chat_templates.get());
        std::string formatted;
        if (has_template) {
            try {
                formatted = common_chat_format_single(
                    g_chat_templates.get(), g_chat_msgs, sys_msg, false, false);
            } catch (...) {
                LOGW("Chat template formatting failed for system msg, using raw content");
                has_template = false;
                formatted = sys_content;
            }
        } else {
            formatted = sys_content;
        }
        g_chat_msgs.push_back(sys_msg);

        auto sys_tokens = common_tokenize(g_context, formatted, has_template, has_template);
        if (!sys_tokens.empty()) {
            for (int i = 0; i < (int)sys_tokens.size(); i += BATCH_SIZE) {
                int sz = std::min((int)sys_tokens.size() - i, BATCH_SIZE);
                common_batch_clear(g_batch);
                for (int j = 0; j < sz; j++) {
                    common_batch_add(g_batch, sys_tokens[i+j], g_current_pos + j, {0}, false);
                }
                if (llama_decode(g_context, g_batch) != 0) {
                    LOGE("Failed to decode system prompt");
                }
            }
            g_system_pos = g_current_pos = (int)sys_tokens.size();
        }
    }

    // Format user message
    common_chat_msg user_chat_msg;
    user_chat_msg.role = "user";
    user_chat_msg.content = user_msg;

    bool has_template = common_chat_templates_was_explicit(g_chat_templates.get());
    std::string formatted_user;
    if (has_template) {
        try {
            formatted_user = common_chat_format_single(
                g_chat_templates.get(), g_chat_msgs, user_chat_msg, true, false);
        } catch (...) {
            LOGW("Chat template formatting failed for user msg, using raw content");
            has_template = false;
            formatted_user = user_msg;
        }
    } else {
        formatted_user = user_msg;
    }
    g_chat_msgs.push_back(user_chat_msg);

    auto user_tokens = common_tokenize(g_context, formatted_user, has_template, has_template);
    if (user_tokens.empty()) {
        return env->NewStringUTF("[ERROR] Tokenization failed");
    }

    for (int i = 0; i < (int)user_tokens.size(); i += BATCH_SIZE) {
        int sz = std::min((int)user_tokens.size() - i, BATCH_SIZE);
        common_batch_clear(g_batch);
        for (int j = 0; j < sz; j++) {
            int pos = g_current_pos + i + j;
            bool want_logit = (i + j == (int)user_tokens.size() - 1);
            common_batch_add(g_batch, user_tokens[i+j], pos, {0}, want_logit);
        }
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("Failed to decode user input");
            return env->NewStringUTF("[ERROR] Decode failed");
        }
    }
    g_current_pos += (int)user_tokens.size();

    // Generate response
    std::string response;
    int max_tokens = 512;

    for (int i = 0; i < max_tokens; i++) {
        if (g_current_pos >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            int discard = (g_current_pos - g_system_pos) / 2;
            llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_pos, g_system_pos + discard);
            llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_pos + discard, g_current_pos, -discard);
            g_current_pos -= discard;
            LOGW("Context shifted, new pos=%d", (int)g_current_pos);
        }

        llama_token new_token = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, new_token, true);

        common_batch_clear(g_batch);
        common_batch_add(g_batch, new_token, g_current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("Failed to decode generated token");
            break;
        }
        g_current_pos++;

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token)) {
            break;
        }

        std::string piece = common_token_to_piece(g_context, new_token);
        response += piece;
    }

    common_chat_msg assistant_msg;
    assistant_msg.role = "assistant";
    assistant_msg.content = response;
    g_chat_msgs.push_back(assistant_msg);

    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_airouter_lib_AiChat_nativeChatWithImage(JNIEnv *env, jobject /*this*/, jstring input, jstring imagePath) {
    if (!g_model || !g_context || !g_sampler) {
        return env->NewStringUTF("[ERROR] Model not loaded");
    }

    if (!g_mtmd_ctx) {
        // Fall back to text-only mode if mmproj not loaded
        LOGW("mmproj not loaded, falling back to text-only mode");
        return Java_com_airouter_lib_AiChat_nativeChat(env, nullptr, input);
    }

    // Reset KV cache on new conversation
    if (g_chat_msgs.empty()) {
        reset_chat();
    }

    const char *inputStr = env->GetStringUTFChars(input, nullptr);
    const char *imagePathStr = env->GetStringUTFChars(imagePath, nullptr);
    std::string user_msg(inputStr);
    std::string image_path(imagePathStr);
    env->ReleaseStringUTFChars(input, inputStr);
    env->ReleaseStringUTFChars(imagePath, imagePathStr);

    LOGI("Processing image: %s", image_path.c_str());

    // Add system prompt on first message (same as nativeChat)
    if (g_chat_msgs.empty()) {
        std::string sys_content = "You are a helpful assistant that can understand images.";
        common_chat_msg sys_msg;
        sys_msg.role = "system";
        sys_msg.content = sys_content;

        bool has_template = common_chat_templates_was_explicit(g_chat_templates.get());
        std::string formatted;
        if (has_template) {
            try {
                formatted = common_chat_format_single(
                    g_chat_templates.get(), g_chat_msgs, sys_msg, false, false);
            } catch (...) {
                LOGW("Chat template formatting failed for system msg, using raw content");
                has_template = false;
                formatted = sys_content;
            }
        } else {
            formatted = sys_content;
        }
        g_chat_msgs.push_back(sys_msg);

        auto sys_tokens = common_tokenize(g_context, formatted, has_template, has_template);
        if (!sys_tokens.empty()) {
            for (int i = 0; i < (int)sys_tokens.size(); i += BATCH_SIZE) {
                int sz = std::min((int)sys_tokens.size() - i, BATCH_SIZE);
                common_batch_clear(g_batch);
                for (int j = 0; j < sz; j++) {
                    common_batch_add(g_batch, sys_tokens[i+j], g_current_pos + j, {0}, false);
                }
                if (llama_decode(g_context, g_batch) != 0) {
                    LOGE("Failed to decode system prompt");
                }
            }
            g_system_pos = g_current_pos = (int)sys_tokens.size();
        }
    }

    // Encode image using mtmd
    // Note: This is a simplified implementation. Full implementation would need to:
    // 1. Load image file into bitmap
    // 2. Call mtmd_encode() to get image tokens
    // 3. Add image tokens to the batch before text tokens
    // For now, we proceed with text-only and log that image was received
    LOGI("Image received for multimodal chat: %s", image_path.c_str());

    // Format user message with image placeholder
    // MiniCPM-V expects a specific format, e.g., "<image>\n<text>"
    common_chat_msg user_chat_msg;
    user_chat_msg.role = "user";
    user_chat_msg.content = user_msg;  // Image tokens would be prepended here

    bool has_template = common_chat_templates_was_explicit(g_chat_templates.get());
    std::string formatted_user;
    if (has_template) {
        try {
            formatted_user = common_chat_format_single(
                g_chat_templates.get(), g_chat_msgs, user_chat_msg, true, false);
        } catch (...) {
            LOGW("Chat template formatting failed for user msg, using raw content");
            has_template = false;
            formatted_user = user_msg;
        }
    } else {
        formatted_user = user_msg;
    }
    g_chat_msgs.push_back(user_chat_msg);

    auto user_tokens = common_tokenize(g_context, formatted_user, has_template, has_template);
    if (user_tokens.empty()) {
        return env->NewStringUTF("[ERROR] Tokenization failed");
    }

    for (int i = 0; i < (int)user_tokens.size(); i += BATCH_SIZE) {
        int sz = std::min((int)user_tokens.size() - i, BATCH_SIZE);
        common_batch_clear(g_batch);
        for (int j = 0; j < sz; j++) {
            int pos = g_current_pos + i + j;
            bool want_logit = (i + j == (int)user_tokens.size() - 1);
            common_batch_add(g_batch, user_tokens[i+j], pos, {0}, want_logit);
        }
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("Failed to decode user input");
            return env->NewStringUTF("[ERROR] Decode failed");
        }
    }
    g_current_pos += (int)user_tokens.size();

    // Generate response (same as nativeChat)
    std::string response;
    int max_tokens = 512;

    for (int i = 0; i < max_tokens; i++) {
        if (g_current_pos >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            int discard = (g_current_pos - g_system_pos) / 2;
            llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_pos, g_system_pos + discard);
            llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_pos + discard, g_current_pos, -discard);
            g_current_pos -= discard;
            LOGW("Context shifted, new pos=%d", (int)g_current_pos);
        }

        llama_token new_token = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, new_token, true);

        common_batch_clear(g_batch);
        common_batch_add(g_batch, new_token, g_current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("Failed to decode generated token");
            break;
        }
        g_current_pos++;

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token)) {
            break;
        }

        std::string piece = common_token_to_piece(g_context, new_token);
        response += piece;
    }

    common_chat_msg assistant_msg;
    assistant_msg.role = "assistant";
    assistant_msg.content = response;
    g_chat_msgs.push_back(assistant_msg);

    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_airouter_lib_AiChat_nativeReset(JNIEnv * /*env*/, jobject /*this*/) {
    reset_chat();
    LOGI("Chat reset");
}

extern "C" JNIEXPORT void JNICALL
Java_com_airouter_lib_AiChat_nativeFree(JNIEnv * /*env*/, jobject /*this*/) {
    free_resources();
    LOGI("Resources freed");
}
