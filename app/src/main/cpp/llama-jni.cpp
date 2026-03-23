#include <jni.h>
#include <android/log.h>
#include <string>
#include <sstream>
#include <unistd.h>

#include "logging.h"
#include "common.h"
#include "sampling.h"
#include "chat.h"
#include "llama.h"
#include "ggml-backend.h"

#ifdef GGML_VULKAN
#include "ggml-vulkan.h"
#endif

// ---------------------------------------------------------------------------
// Global state (llama.cpp is NOT thread-safe — single-threaded access only)
// ---------------------------------------------------------------------------
static llama_model                  * g_model   = nullptr;
static llama_context                * g_context  = nullptr;
static llama_batch                    g_batch;
static common_chat_templates_ptr      g_chat_templates;
static common_sampler               * g_sampler  = nullptr;
static llama_adapter_lora           * g_lora     = nullptr;  // Phase 10 (v1.9.0)

// Position tracking
static llama_pos g_system_prompt_pos = 0;
static llama_pos g_current_pos       = 0;

// Cancellation flag (volatile for cross-thread visibility)
static volatile bool g_abort = false;

// Short-term state
static std::string       g_cached_token_chars;
static std::ostringstream g_assistant_ss;

// Last native error message (populated on error code 3)
static std::string g_last_native_error;

// Chat history for template formatting
static std::vector<common_chat_msg> g_chat_msgs;

// Configuration
static int             g_batch_size         = 512;
static constexpr int   OVERFLOW_HEADROOM    = 4;
static constexpr float DEFAULT_SAMPLER_TEMP = 0.7f;

// Sampling params set via nativeSetSamplingParams()
static float g_sampler_temp          = 0.7f;
static float g_sampler_top_p         = 0.95f;
static float g_sampler_repeat_pen    = 1.1f;
static int   g_sampler_top_k         = 40;
static float g_sampler_min_p         = 0.05f;
static int   g_sampler_penalty_last_n = 64;

static int g_n_ctx = 2048;

// ---------------------------------------------------------------------------
// Helper: UTF-8 validation
// ---------------------------------------------------------------------------
static bool is_valid_utf8(const char *string) {
    if (!string) return true;
    const auto *bytes = (const unsigned char *) string;
    int num;
    while (*bytes != 0x00) {
        if ((*bytes & 0x80) == 0x00)      num = 1;
        else if ((*bytes & 0xE0) == 0xC0) num = 2;
        else if ((*bytes & 0xF0) == 0xE0) num = 3;
        else if ((*bytes & 0xF8) == 0xF0) num = 4;
        else return false;
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) return false;
            bytes += 1;
        }
    }
    return true;
}

// ---------------------------------------------------------------------------
// Helper: Reset states
// ---------------------------------------------------------------------------
static void reset_long_term_states(bool clear_kv_cache = true) {
    g_chat_msgs.clear();
    g_system_prompt_pos = 0;
    g_current_pos = 0;
    if (clear_kv_cache && g_context)
        llama_memory_clear(llama_get_memory(g_context), true);
}

static void reset_short_term_states() {
    g_cached_token_chars.clear();
    g_assistant_ss.str("");
    g_abort = false;
}

// ---------------------------------------------------------------------------
// Helper: Context shifting
// ---------------------------------------------------------------------------
static void shift_context() {
    const int n_discard = (g_current_pos - g_system_prompt_pos) / 2;
    LOGi("%s: Discarding %d tokens", __func__, n_discard);
    llama_memory_seq_rm(llama_get_memory(g_context), 0, g_system_prompt_pos, g_system_prompt_pos + n_discard);
    llama_memory_seq_add(llama_get_memory(g_context), 0, g_system_prompt_pos + n_discard, g_current_pos, -n_discard);
    g_current_pos -= n_discard;
}

// ---------------------------------------------------------------------------
// Helper: Decode tokens in batches
// ---------------------------------------------------------------------------
static int decode_tokens_in_batches(
        llama_context *context,
        llama_batch &batch,
        const llama_tokens &tokens,
        const llama_pos start_pos,
        const bool compute_last_logit = false)
{
    LOGd("%s: Decode %d tokens starting at position %d", __func__, (int)tokens.size(), start_pos);
    for (int i = 0; i < (int)tokens.size(); i += g_batch_size) {
        const int cur_batch_size = std::min((int)tokens.size() - i, g_batch_size);
        common_batch_clear(batch);

        if (start_pos + i + cur_batch_size >= g_n_ctx - OVERFLOW_HEADROOM) {
            LOGw("%s: Context full! Shifting...", __func__);
            shift_context();
        }

        for (int j = 0; j < cur_batch_size; j++) {
            const llama_token token_id = tokens[i + j];
            const llama_pos position = start_pos + i + j;
            const bool want_logit = compute_last_logit && (i + j == (int)tokens.size() - 1);
            common_batch_add(batch, token_id, position, {0}, want_logit);
        }

        const int decode_result = llama_decode(context, batch);
        if (decode_result) {
            LOGe("%s: llama_decode failed w/ %d", __func__, decode_result);
            return 1;
        }
    }
    return 0;
}

// ---------------------------------------------------------------------------
// Helper: Chat formatting
// ---------------------------------------------------------------------------
static std::string chat_add_and_format(const std::string &role, const std::string &content) {
    common_chat_msg new_msg;
    new_msg.role = role;
    new_msg.content = content;
    auto formatted = common_chat_format_single(
            g_chat_templates.get(), g_chat_msgs, new_msg, role == "user", false);
    g_chat_msgs.push_back(new_msg);
    LOGd("%s: Formatted %s message: \n%s\n", __func__, role.c_str(), formatted.c_str());
    return formatted;
}

// ===========================================================================
// JNI Functions — package: com.quantma.lite.data.inference.LlamaJni
// ===========================================================================

#define JNI_PREFIX Java_com_quantma_lite_data_inference_LlamaJni_

extern "C" {

// ---------------------------------------------------------------------------
// 1. nativeInit()
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeInit(
        JNIEnv *env, jobject, jstring jNativeLibDir)
{
    llama_log_set(codeagent_android_log_callback, nullptr);

    // Load CPU backend variants from the native lib directory
    const auto *path = env->GetStringUTFChars(jNativeLibDir, 0);
    LOGi("Loading backends from: %s", path);
    ggml_backend_load_all_from_path(path);
    env->ReleaseStringUTFChars(jNativeLibDir, path);

    llama_backend_init();
    LOGi("llama backend initialized successfully.");
}

// ---------------------------------------------------------------------------
// 2. nativeLoadModel(modelPath, nThreads, contextSize, nGpuLayers) -> Int
// Phase 7 (v1.6.0): added nGpuLayers for Vulkan GPU offload
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeLoadModel(
        JNIEnv *env, jobject, jstring jModelPath, jint nThreads, jint contextSize,
        jint nGpuLayers, jint batchSize, jboolean flashAttention, jboolean mlock)
{
    // Load model
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = nGpuLayers;  // 0 = CPU only; >0 = GPU layers
    model_params.use_mlock    = mlock;       // Lock model in RAM
    const auto *model_path = env->GetStringUTFChars(jModelPath, 0);
    LOGi("%s: Loading model from: %s (gpu_layers=%d, mlock=%d)", __func__, model_path, nGpuLayers, (int)mlock);

    auto *model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(jModelPath, model_path);
    if (!model) {
        LOGe("%s: Failed to load model!", __func__);
        return 1;
    }
    g_model = model;

    // Store dynamic batch size
    g_batch_size = (batchSize > 0) ? batchSize : 512;

    // Create context
    g_n_ctx = contextSize;
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx           = contextSize;
    ctx_params.n_batch         = g_batch_size;
    ctx_params.n_ubatch        = g_batch_size;
    ctx_params.n_threads       = nThreads;
    ctx_params.n_threads_batch = nThreads;
    ctx_params.flash_attn_type = flashAttention ? LLAMA_FLASH_ATTN_TYPE_ENABLED : LLAMA_FLASH_ATTN_TYPE_DISABLED;

    auto *context = llama_init_from_model(g_model, ctx_params);
    if (!context) {
        LOGe("%s: Failed to create context!", __func__);
        llama_model_free(g_model);
        g_model = nullptr;
        return 2;
    }
    g_context = context;

    // Init batch, chat templates, sampler
    g_batch = llama_batch_init(g_batch_size, 0, 1);
    g_chat_templates = common_chat_templates_init(g_model, "");
    g_sampler = nullptr; // Will be created on first completion

    LOGi("%s: Model loaded! ctx=%d, threads=%d, gpu=%d, batch=%d, flash=%d, mlock=%d",
         __func__, contextSize, nThreads, nGpuLayers, g_batch_size, (int)flashAttention, (int)mlock);
    return 0;
}

// ---------------------------------------------------------------------------
// 2b. nativeGetGpuInfo() -> String  [Phase 7 v1.6.0, updated v2.5.x]
// Enumerates all registered ggml acceleration backends (Hexagon, Vulkan, etc.)
// Returns e.g. "Hexagon | Vulkan: Adreno 830 (8192 MB)" or "CPU only".
// Must be called AFTER nativeInit() so backends are loaded from native lib dir.
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeGetGpuInfo(JNIEnv *env, jobject)
{
    std::string result;

    // Enumerate all registered ggml backends; skip pure CPU
    const size_t backend_count = ggml_backend_reg_count();
    for (size_t i = 0; i < backend_count; i++) {
        ggml_backend_reg_t reg = ggml_backend_reg_get(i);
        const char * name = ggml_backend_reg_name(reg);
        if (!name) continue;
        // Skip the pure CPU backend — we want accelerators only
        if (std::string(name) == "CPU") continue;
        if (!result.empty()) result += " | ";
        result += name;
    }

#ifdef GGML_VULKAN
    // Append Vulkan device name + VRAM to the "Vulkan" entry if present
    try {
        int vk_count = ggml_backend_vk_get_device_count();
        if (vk_count > 0) {
            char desc[256] = {0};
            ggml_backend_vk_get_device_description(0, desc, sizeof(desc));
            size_t free_mem = 0, total_mem = 0;
            ggml_backend_vk_get_device_memory(0, &free_mem, &total_mem);
            std::string detail(desc);
            if (total_mem > 0) {
                detail += " (" + std::to_string(total_mem / (1024 * 1024)) + " MB)";
            }
            // Replace bare "Vulkan" token with full detail
            size_t pos = result.find("Vulkan");
            if (pos != std::string::npos) {
                result.insert(pos + 6, ": " + detail);
            }
        }
    } catch (...) {}
#endif

    if (result.empty()) result = "CPU only";
    return env->NewStringUTF(result.c_str());
}

// ---------------------------------------------------------------------------
// 3. nativeUnloadModel()
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeUnloadModel(
        JNIEnv *, jobject)
{
    reset_long_term_states(false);
    reset_short_term_states();

    if (g_sampler)  { common_sampler_free(g_sampler); g_sampler = nullptr; }
    g_chat_templates.reset();
    if (g_context)  { llama_batch_free(g_batch); llama_free(g_context); g_context = nullptr; }
    g_lora = nullptr;  // adapter lifetime managed by llama_model destructor
    if (g_model)    { llama_model_free(g_model); g_model = nullptr; }

    LOGi("Model unloaded.");
}

// ---------------------------------------------------------------------------
// 3.5. nativeSetSamplingParams(temperature, topP, repeatPenalty) — Phase 1
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeSetSamplingParams(
        JNIEnv *, jobject, jfloat temperature, jfloat topP, jfloat repeatPenalty,
        jint topK, jfloat minP, jint penaltyLastN)
{
    g_sampler_temp           = temperature;
    g_sampler_top_p          = topP;
    g_sampler_repeat_pen     = repeatPenalty;
    g_sampler_top_k          = topK;
    g_sampler_min_p          = minP;
    g_sampler_penalty_last_n = penaltyLastN;
    LOGi("Sampling params: temp=%.2f, top_p=%.2f, repeat=%.2f, top_k=%d, min_p=%.3f, pen_last_n=%d",
         g_sampler_temp, g_sampler_top_p, g_sampler_repeat_pen,
         g_sampler_top_k, g_sampler_min_p, g_sampler_penalty_last_n);
}

// ---------------------------------------------------------------------------
// 3.6. nativeLoadLoraAdapter(path, scale) -> Int   [Phase 10 v1.9.0]
// Returns: 0=success, 1=model not loaded, 2=adapter load failed
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeLoadLoraAdapter(
        JNIEnv *env, jobject, jstring jPath, jfloat scale)
{
    if (!g_model) {
        LOGe("%s: Model not loaded!", __func__);
        return 1;
    }

    // Clear previous adapter pointer (lifetime managed by llama_model)
    g_lora = nullptr;

    const char *path = env->GetStringUTFChars(jPath, nullptr);
    LOGi("%s: Loading LoRA adapter from: %s (scale=%.2f)", __func__, path, (float)scale);

    g_lora = llama_adapter_lora_init(g_model, path);
    env->ReleaseStringUTFChars(jPath, path);

    if (!g_lora) {
        LOGe("%s: Failed to load LoRA adapter!", __func__);
        return 2;
    }

    // Apply adapter to context with scale
    if (g_context) {
        float scale_val = (float)scale;
        llama_set_adapters_lora(g_context, &g_lora, 1, &scale_val);
    }

    LOGi("%s: LoRA adapter loaded successfully (scale=%.2f)", __func__, (float)scale);
    return 0;
}

// ---------------------------------------------------------------------------
// 3.7. nativeUnloadLoraAdapter()   [Phase 10 v1.9.0]
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeUnloadLoraAdapter(
        JNIEnv *, jobject)
{
    if (g_lora) {
        g_lora = nullptr;  // adapter lifetime managed by llama_model destructor
        LOGi("LoRA adapter unloaded.");
    }
}

// ---------------------------------------------------------------------------
// 4. nativeStartCompletion(prompt, maxTokens)
// ---------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeStartCompletion(
        JNIEnv *env, jobject, jstring jPrompt, jint maxTokens)
{
    if (!g_model || !g_context) {
        LOGe("%s: Model not loaded!", __func__);
        return 1;
    }

    reset_short_term_states();

    // Create/reset sampler with current params from nativeSetSamplingParams()
    if (g_sampler) { common_sampler_free(g_sampler); }
    common_params_sampling sparams;
    sparams.temp              = g_sampler_temp;
    sparams.top_p             = g_sampler_top_p;
    sparams.top_k             = g_sampler_top_k;
    sparams.min_p             = g_sampler_min_p;
    sparams.penalty_repeat    = g_sampler_repeat_pen;
    sparams.penalty_last_n    = g_sampler_penalty_last_n;
    g_sampler = common_sampler_init(g_model, sparams);

    // Reset context
    reset_long_term_states(true);

    // Get prompt string
    const auto *prompt = env->GetStringUTFChars(jPrompt, nullptr);
    std::string prompt_str(prompt);
    env->ReleaseStringUTFChars(jPrompt, prompt);

    LOGd("%s: Processing prompt (%d chars)", __func__, (int)prompt_str.length());

    // Tokenize prompt
    auto tokens = common_tokenize(g_context, prompt_str, true, true);
    LOGi("%s: Prompt tokenized: %d tokens", __func__, (int)tokens.size());

    // Truncate if too long
    const int max_prompt_size = g_n_ctx - OVERFLOW_HEADROOM - maxTokens;
    if ((int)tokens.size() > max_prompt_size && max_prompt_size > 0) {
        const int skipped = (int)tokens.size() - max_prompt_size;
        tokens.resize(max_prompt_size);
        LOGw("%s: Prompt truncated! Skipped %d tokens", __func__, skipped);
    }

    // Decode prompt tokens in batches (retry once on failure after full cache clear)
    try {
        if (decode_tokens_in_batches(g_context, g_batch, tokens, 0, true)) {
            LOGw("%s: llama_decode() failed, retrying after KV cache defrag...", __func__);
            llama_memory_clear(llama_get_memory(g_context), true);
            g_current_pos = 0;
            if (decode_tokens_in_batches(g_context, g_batch, tokens, 0, true)) {
                LOGe("%s: llama_decode() failed on retry!", __func__);
                return 2;
            }
        }
    } catch (const std::exception& e) {
        g_last_native_error = e.what();
        LOGe("%s: native exception during decode: %s", __func__, e.what());
        return 3;
    } catch (...) {
        g_last_native_error = "unknown native exception during decode";
        LOGe("%s: unknown native exception during decode", __func__);
        return 3;
    }

    g_current_pos = (int)tokens.size();
    LOGi("%s: Prompt processed. Position: %d, maxTokens: %d", __func__, g_current_pos, maxTokens);
    return 0;
}

// ---------------------------------------------------------------------------
// 5. nativeNextToken() -> String? (null = end of generation)
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeNextToken(
        JNIEnv *env, jobject)
{
    // Check cancellation
    if (g_abort) {
        LOGi("%s: Generation aborted by user", __func__);
        return nullptr;
    }

    if (!g_context || !g_sampler) {
        LOGe("%s: No active context/sampler!", __func__);
        return nullptr;
    }

    // Context overflow handling
    if (g_current_pos >= g_n_ctx - OVERFLOW_HEADROOM) {
        LOGw("%s: Context full! Shifting...", __func__);
        shift_context();
    }

    // Sample next token
    const auto new_token_id = common_sampler_sample(g_sampler, g_context, -1);
    common_sampler_accept(g_sampler, new_token_id, true);

    // Decode the new token
    common_batch_clear(g_batch);
    common_batch_add(g_batch, new_token_id, g_current_pos, {0}, true);
    try {
        if (llama_decode(g_context, g_batch) != 0) {
            LOGe("%s: llama_decode() failed for generated token", __func__);
            return nullptr;
        }
    } catch (const std::exception& e) {
        LOGe("%s: native exception during token decode: %s", __func__, e.what());
        return nullptr;
    } catch (...) {
        LOGe("%s: unknown native exception during token decode", __func__);
        return nullptr;
    }
    g_current_pos++;

    // Check for end of generation
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), new_token_id)) {
        LOGd("%s: EOG token reached", __func__);
        return nullptr;
    }

    // Convert token to text with UTF-8 buffering
    auto new_token_chars = common_token_to_piece(g_context, new_token_id);
    g_cached_token_chars += new_token_chars;

    jstring result;
    if (is_valid_utf8(g_cached_token_chars.c_str())) {
        result = env->NewStringUTF(g_cached_token_chars.c_str());
        g_assistant_ss << g_cached_token_chars;
        g_cached_token_chars.clear();
    } else {
        // Incomplete UTF-8 sequence — return empty and wait for more bytes
        result = env->NewStringUTF("");
    }
    return result;
}

// ---------------------------------------------------------------------------
// 6. nativeAbort()
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeAbort(
        JNIEnv *, jobject)
{
    g_abort = true;
    LOGi("Generation abort requested.");
}

// ---------------------------------------------------------------------------
// 7. nativeSystemInfo() -> String
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeSystemInfo(
        JNIEnv *env, jobject)
{
    return env->NewStringUTF(llama_print_system_info());
}

// ---------------------------------------------------------------------------
// 8. nativeShutdown()
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeShutdown(
        JNIEnv *, jobject)
{
    llama_backend_free();
    LOGi("llama backend shut down.");
}

// ---------------------------------------------------------------------------
// 9. nativeGetLastError() -> String
// Returns the last native error message (set on error code 3).
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_quantma_lite_data_inference_LlamaJni_nativeGetLastError(
        JNIEnv *env, jobject)
{
    return env->NewStringUTF(g_last_native_error.c_str());
}

} // extern "C"
