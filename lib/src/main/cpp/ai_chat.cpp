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
#include "mtmd-helper.h"

#define TAG "AiChat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// ---- Configuration ----
constexpr int   N_THREADS_MIN        = 2;
constexpr int   N_THREADS_MAX        = 4;
constexpr int   N_THREADS_HEADROOM   = 2;
constexpr int   DEFAULT_CONTEXT_SIZE = 4096;
constexpr int   BATCH_SIZE           = 2048;
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
    mtmd_params.n_threads = get_n_threads();
    
    // Note: mtmd_init_from_file requires the loaded LLM model pointer
    g_mtmd_ctx = mtmd_init_from_file(mmproj_path, g_model, mtmd_params);
    
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
        LOGE("mmproj not loaded, cannot process image");
        return env->NewStringUTF("[ERROR] 视觉模型(mmproj)未加载\n请检查：\n1. 模型管理页面两个文件都下载了？\n2. 重启应用后再试");
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

    // --- Encode image using mtmd ---
    LOGI("Loading image: %s", image_path.c_str());

    mtmd_bitmap * bitmap = mtmd_helper_bitmap_init_from_file(g_mtmd_ctx, image_path.c_str());
    if (!bitmap) {
        LOGE("Failed to load image file: %s", image_path.c_str());
        std::string err = "[ERROR] 无法加载图片文件，请确认图片格式是否支持（jpg/png/bmp）\n路径: " + image_path;
        return env->NewStringUTF(err.c_str());
    }

    // Build prompt with media marker for mtmd_tokenize
    const char * marker = mtmd_default_marker();
    std::string prompt_with_marker = std::string(marker) + "\n" + user_msg;

    // Tokenize image + text together
    mtmd_input_chunks * chunks = mtmd_input_chunks_init();
    mtmd_input_text text_input;
    text_input.text = prompt_with_marker.c_str();
    text_input.add_special = true;
    text_input.parse_special = true;

    const mtmd_bitmap * bitmaps[] = { bitmap };
    int32_t ret = mtmd_tokenize(g_mtmd_ctx, chunks, &text_input, bitmaps, 1);
    if (ret != 0) {
        LOGE("mtmd_tokenize failed with code %d", ret);
        mtmd_input_chunks_free(chunks);
        mtmd_bitmap_free(bitmap);
        std::string err = "[ERROR] 图片编码失败 (mtmd_tokenize error code: " + std::to_string(ret) + ")";
        return env->NewStringUTF(err.c_str());
    }

    mtmd_bitmap_free(bitmap);
    LOGI("mtmd_tokenize success, %zu chunks, %zu total tokens",
         mtmd_input_chunks_size(chunks),
         mtmd_helper_get_n_tokens(chunks));

    // Eval all chunks (handles image encoding + text decoding automatically)
    LOGI("========== Starting mtmd_helper_eval_chunks ==========");
    llama_pos new_n_past = g_current_pos;
    LOGI("mtmd_helper_eval_chunks: g_current_pos=%d, BATCH_SIZE=%d", (int)g_current_pos, BATCH_SIZE);
    
    ret = mtmd_helper_eval_chunks(g_mtmd_ctx, g_context, chunks,
                                   g_current_pos, 0, BATCH_SIZE, true, &new_n_past);
    mtmd_input_chunks_free(chunks);

    if (ret != 0) {
        LOGE("mtmd_helper_eval_chunks FAILED with code %d", ret);
        return env->NewStringUTF("[ERROR] Failed to process image");
    }

    LOGI("mtmd_helper_eval_chunks SUCCESS, new_n_past=%d", (int)new_n_past);
    g_current_pos = new_n_past;
    g_chat_msgs.push_back({"user", user_msg});
    LOGI("Image + text processed, g_current_pos=%d", (int)g_current_pos);
    LOGI("========== Starting inference loop ==========");

    // Generate response (same as nativeChat)
    std::string response;
    int max_tokens = 512;
    LOGI("Inference loop: max_tokens=%d, g_current_pos=%d", max_tokens, (int)g_current_pos);

    for (int i = 0; i < max_tokens; i++) {
        if (g_current_pos >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            int discard = (g_current_pos - g_system_pos) / 2;
            LOGW("Context overflow, shifting: discard=%d, old_pos=%d", discard, (int)g_current_pos);
            llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_pos, g_system_pos + discard);
            llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_pos + discard, g_current_pos, -discard);
            g_current_pos -= discard;
            LOGW("Context shifted, new pos=%d", (int)g_current_pos);
        }

        llama_token new_token = common_sampler_sample(g_sampler, g_context, -1);
        
        // Log first 5 tokens for debugging
        if (i < 5) {
            LOGI("Inference loop: i=%d, new_token=%d", i, (int)new_token);
        }
        
        common_sampler_accept(g_sampler, new_token, true);

        common_batch_clear(g_batch);
        common_batch_add(g_batch, new_token, g_current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("Failed to decode generated token at i=%d", i);
            break;
        }
        g_current_pos++;

        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token)) {
            LOGI("Inference loop: EOS token detected at i=%d", i);
            break;
        }

        std::string piece = common_token_to_piece(g_context, new_token);
        response += piece;
        
        // Log response growth every 50 tokens
        if (i % 50 == 0) {
            LOGI("Inference loop: i=%d, response length=%zu", i, response.size());
        }
    }
    
    LOGI("Inference loop finished: response length=%zu", response.size());
    if (response.empty()) {
        LOGW("Inference loop: response is EMPTY!");
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
