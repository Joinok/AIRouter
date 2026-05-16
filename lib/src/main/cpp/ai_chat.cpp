#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <android/log.h>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"

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
static llama_batch                  g_batch;
static common_chat_templates_ptr    g_chat_templates;
static common_sampler             * g_sampler = nullptr;
static std::vector<common_chat_msg> g_chat_msgs;
static llama_pos                    g_system_pos   = 0;
static llama_pos                    g_current_pos  = 0;

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

    // Load model
    llama_model_params mparams = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!g_model) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    // Create context
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
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    // Init batch, sampler, chat templates
    g_batch = llama_batch_init(BATCH_SIZE, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");

    common_params_sampling sparams;
    sparams.temp = DEFAULT_TEMP;
    g_sampler = common_sampler_init(g_model, sparams);

    reset_chat();
    LOGI("Model loaded, ctx=%d, threads=%d", n_ctx, n_threads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_airouter_lib_AiChat_nativeChat(JNIEnv *env, jobject /*this*/, jstring input) {
    if (!g_model || !g_context || !g_sampler) {
        return env->NewStringUTF("[ERROR] Model not loaded");
    }

    // Reset short-term generation state
    g_current_pos = 0;
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
            formatted = common_chat_format_single(
                g_chat_templates.get(), g_chat_msgs, sys_msg, false, false);
        } else {
            formatted = sys_content;
        }
        g_chat_msgs.push_back(sys_msg);

        // Tokenize and decode system prompt
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
        formatted_user = common_chat_format_single(
            g_chat_templates.get(), g_chat_msgs, user_chat_msg, true, false);
    } else {
        formatted_user = user_msg;
    }
    g_chat_msgs.push_back(user_chat_msg);

    // Tokenize user input
    auto user_tokens = common_tokenize(g_context, formatted_user, has_template, has_template);
    if (user_tokens.empty()) {
        return env->NewStringUTF("[ERROR] Tokenization failed");
    }

    // Decode user tokens in batches
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

    // Generate response (max 512 tokens)
    std::string response;
    std::string cached_chars;
    int max_tokens = 512;

    for (int i = 0; i < max_tokens; i++) {
        // Check context overflow
        if (g_current_pos >= DEFAULT_CONTEXT_SIZE - OVERFLOW_HEADROOM) {
            // Simple context shift: discard half of user tokens
            int discard = (g_current_pos - g_system_pos) / 2;
            llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_pos, g_system_pos + discard);
            llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_pos + discard, g_current_pos, -discard);
            g_current_pos -= discard;
            LOGW("Context shifted, new pos=%d", (int)g_current_pos);
        }

        // Sample next token
        llama_token new_token = common_sampler_sample(g_sampler, g_context, -1);
        common_sampler_accept(g_sampler, new_token, true);

        // Decode the new token
        common_batch_clear(g_batch);
        common_batch_add(g_batch, new_token, g_current_pos, {0}, true);
        if (llama_decode(g_context, g_batch) != 0) {
            LOGE("Failed to decode generated token");
            break;
        }
        g_current_pos++;

        // Check for EOG
        if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token)) {
            break;
        }

        // Convert token to text
        std::string piece = common_token_to_piece(g_context, new_token);
        cached_chars += piece;

        // Emit complete UTF-8 sequences
        // Simple check: if piece ends with valid boundary
        response += piece;
    }

    // Add assistant message to chat history
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
    reset_chat();
    common_sampler_free(g_sampler);
    g_sampler = nullptr;
    g_chat_templates.reset();
    llama_batch_free(g_batch);
    llama_free(g_context);
    g_context = nullptr;
    llama_model_free(g_model);
    g_model = nullptr;
    LOGI("Resources freed");
}
