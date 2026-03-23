package com.quantma.lite.data.model

/**
 * Catalog of recommended GGUF models with detailed descriptions.
 * Phase 9 (v1.9.1) → expanded v2.0 with categories, specs, and multimodal models.
 */

/** Model category for filtering and display. */
enum class ModelCategory {
    COMPACT,      // Small models for low-RAM devices
    CODING,       // Code-specialized models
    GENERAL,      // General-purpose instruction models
    MULTIMODAL    // Vision + text models
}

data class RecommendedModel(
    val name: String,
    val description: String,
    val detailedDescription: String,
    val sizeLabel: String,
    val paramCount: String,
    val quantization: String,
    val ramRequired: String,
    val category: ModelCategory,
    val capabilities: List<String>,
    val url: String,
    val filename: String,
    /** URL for the multimodal vision projector GGUF (null for text-only models). */
    val mmProjUrl: String? = null,
    val mmProjFilename: String? = null
)

object RecommendedModels {
    val list = listOf(

        // ── Compact (low RAM) ───────────────────────────────────────

        RecommendedModel(
            name = "Qwen2.5-Coder 1.5B",
            description = "Ultra-light coder for budget devices. Alibaba.",
            detailedDescription = "Smallest code-specialized model in the Qwen2.5 line. " +
                "Handles autocomplete, simple refactoring, and basic code explanations. " +
                "Ideal for devices with 3–4 GB RAM where larger models won't fit. " +
                "Trained on 5.5T tokens of code and text. " +
                "Limited in complex multi-step reasoning — best for quick fixes and snippets.",
            sizeLabel = "1.1 GB",
            paramCount = "1.5B",
            quantization = "Q4_K_M",
            ramRequired = "3 GB+",
            category = ModelCategory.COMPACT,
            capabilities = listOf("Autocomplete", "Simple refactoring", "Code explanation"),
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf"
        ),

        RecommendedModel(
            name = "Llama 3.2 3B",
            description = "Compact Meta model, great for low-RAM devices.",
            detailedDescription = "Meta's efficient 3B model with strong instruction following. " +
                "Good at explaining code, light editing, and general chat. " +
                "Not code-specialized but surprisingly capable for its size. " +
                "Fits comfortably on 4 GB RAM devices with room for context.",
            sizeLabel = "2.0 GB",
            paramCount = "3B",
            quantization = "Q4_K_M",
            ramRequired = "4 GB+",
            category = ModelCategory.COMPACT,
            capabilities = listOf("Chat", "Code explanation", "Light editing"),
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            filename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5-Coder 3B",
            description = "Best compact coder. Alibaba, 5.5T tokens of code.",
            detailedDescription = "The sweet spot for budget phones: code-specialized 3B model " +
                "trained on 5.5 trillion tokens of source code and synthetic data. " +
                "Handles code generation, refactoring, bug detection, and test writing " +
                "noticeably better than general-purpose models of the same size. " +
                "Reliable JSON tool-use for SAS agent loop. Fits 4 GB RAM.",
            sizeLabel = "2.1 GB",
            paramCount = "3B",
            quantization = "Q4_K_M",
            ramRequired = "4 GB+",
            category = ModelCategory.CODING,
            capabilities = listOf("Code generation", "Refactoring", "Bug search", "Tool-use"),
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-coder-3b-instruct-q4_k_m.gguf"
        ),

        // ── General purpose ─────────────────────────────────────────

        RecommendedModel(
            name = "Phi-3.5 Mini",
            description = "Fast & smart from Microsoft. 3.8B params.",
            detailedDescription = "Microsoft's compact powerhouse. Despite 3.8B parameters, " +
                "it punches above its weight in reasoning and instruction following. " +
                "Strong at code explanation, documentation writing, and structured output. " +
                "Excellent for chat-based workflows where speed matters. " +
                "128K native context (limited by device RAM in practice).",
            sizeLabel = "2.2 GB",
            paramCount = "3.8B",
            quantization = "Q4_K_M",
            ramRequired = "4 GB+",
            category = ModelCategory.GENERAL,
            capabilities = listOf("Reasoning", "Documentation", "Structured output", "Fast inference"),
            url = "https://huggingface.co/bartowski/Phi-3.5-mini-instruct-GGUF/resolve/main/Phi-3.5-mini-instruct-Q4_K_M.gguf",
            filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5 3B Instruct",
            description = "Universal Alibaba model. Chat, advice, learning.",
            detailedDescription = "General-purpose 3B model from Alibaba's Qwen2.5 family. " +
                "Unlike the Coder variant, trained on a balanced mix of text, dialogue, and knowledge. " +
                "Ideal for everyday use: chat, Q&A, translation, advice, learning assistance. " +
                "Multilingual — speaks Russian, English, Chinese, and 27+ other languages natively. " +
                "Best general-purpose model for 4 GB RAM devices.",
            sizeLabel = "2.0 GB",
            paramCount = "3B",
            quantization = "Q4_K_M",
            ramRequired = "4 GB+",
            category = ModelCategory.GENERAL,
            capabilities = listOf("Chat", "Q&A", "Translation", "Multilingual", "Learning"),
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-3b-instruct-q4_k_m.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5 7B Instruct",
            description = "Powerful universal model. Reasoning, analysis, creativity.",
            detailedDescription = "The recommended general-purpose model for flagship phones. " +
                "7B parameters with top-tier instruction following and reasoning. " +
                "Excels at: deep Q&A, writing, analysis, brainstorming, summarization, translation. " +
                "128K context window — can discuss long texts and documents. " +
                "Multilingual: 29+ languages including Russian and English. " +
                "For users who want the best chat experience without code-specific focus.",
            sizeLabel = "4.7 GB",
            paramCount = "7B",
            quantization = "Q4_K_M",
            ramRequired = "6 GB+",
            category = ModelCategory.GENERAL,
            capabilities = listOf("Reasoning", "Writing", "Analysis", "Translation", "128K context"),
            url = "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct-GGUF/resolve/main/qwen2.5-7b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-7b-instruct-q4_k_m.gguf"
        ),

        // ── Coding (main tier) ──────────────────────────────────────

        RecommendedModel(
            name = "CodeLlama 7B Instruct",
            description = "Proven Meta coder. Reliable tool-use baseline.",
            detailedDescription = "Meta's battle-tested code model fine-tuned for instruction following. " +
                "Handles Python, JavaScript, Java, C++, and 20+ languages. " +
                "Reliable baseline for SAS agent loop and Guided Mode. " +
                "Well-studied — most community LoRA adapters target this architecture. " +
                "Good for devices with 6+ GB RAM.",
            sizeLabel = "3.8 GB",
            paramCount = "7B",
            quantization = "Q4_K_M",
            ramRequired = "6 GB+",
            category = ModelCategory.CODING,
            capabilities = listOf("Code generation", "Instruction following", "LoRA ecosystem", "Multi-language"),
            url = "https://huggingface.co/TheBloke/CodeLlama-7B-Instruct-GGUF/resolve/main/codellama-7b-instruct.Q4_K_M.gguf",
            filename = "codellama-7b-instruct.Q4_K_M.gguf"
        ),

        RecommendedModel(
            name = "DeepSeek Coder 6.7B",
            description = "Excellent coder from DeepSeek. Strong reasoning.",
            detailedDescription = "DeepSeek's code-specialized model with outstanding reasoning abilities. " +
                "Excels at multi-step code generation and complex refactoring. " +
                "Trained on 2T tokens of code with project-level understanding. " +
                "Particularly strong in Python, TypeScript, and Go. " +
                "Competitive with larger models on HumanEval and MBPP benchmarks.",
            sizeLabel = "4.1 GB",
            paramCount = "6.7B",
            quantization = "Q4_K_M",
            ramRequired = "6 GB+",
            category = ModelCategory.CODING,
            capabilities = listOf("Code reasoning", "Multi-step generation", "Project context", "Benchmarks leader"),
            url = "https://huggingface.co/TheBloke/deepseek-coder-6.7B-instruct-GGUF/resolve/main/deepseek-coder-6.7b-instruct.Q4_K_M.gguf",
            filename = "deepseek-coder-6.7b-instruct.Q4_K_M.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5-Coder 7B",
            description = "Top open-source coder. Best tool-use at 7B tier.",
            detailedDescription = "The recommended model for most users. " +
                "Trained on 5.5 trillion tokens — the largest code training dataset in open source. " +
                "State-of-the-art at 7B scale on code generation, repair, and reasoning. " +
                "Excellent JSON tool-use compliance for SAS agent loop — " +
                "rarely needs Guided Mode fallback. " +
                "Supports 128K context (use 4096–8192 on mobile for stability).",
            sizeLabel = "4.7 GB",
            paramCount = "7B",
            quantization = "Q4_K_M",
            ramRequired = "6 GB+",
            category = ModelCategory.CODING,
            capabilities = listOf("Code generation", "Tool-use (JSON)", "Bug fixing", "Test writing", "128K context"),
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-coder-7b-instruct-q4_k_m.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5-Coder 7B [Q6_K]",
            description = "Same 7B coder, higher precision. Near-lossless quality.",
            detailedDescription = "Higher-quality quantization of the same Qwen2.5-Coder 7B. " +
                "Q6_K preserves ~99% of the original FP16 model quality vs ~97% for Q4_K_M. " +
                "Noticeably better at nuanced reasoning, complex refactoring, and edge cases. " +
                "Requires more RAM and storage but delivers the best 7B experience. " +
                "Recommended if your device has 8+ GB RAM to spare.",
            sizeLabel = "6.3 GB",
            paramCount = "7B",
            quantization = "Q6_K",
            ramRequired = "8 GB+",
            category = ModelCategory.CODING,
            capabilities = listOf("Code generation", "Precise reasoning", "Tool-use (JSON)", "Near-lossless quality"),
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q6_k.gguf",
            filename = "qwen2.5-coder-7b-instruct-q6_k.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5-Coder 14B",
            description = "Flagship coder. Near GPT-4o on benchmarks.",
            detailedDescription = "The most powerful coding model that fits on a flagship phone. " +
                "14.7B parameters trained on 5.5T tokens of code. " +
                "Near GPT-4o level on coding benchmarks (HumanEval, MBPP, LiveCodeBench). " +
                "Handles complex architecture decisions, large refactors, and multi-file reasoning. " +
                "Autonomous agent loop without Guided Mode — self-plans and self-corrects. " +
                "Requires 12+ GB RAM — for Samsung S24 Ultra, Pixel 9 Pro, OnePlus 12 and similar.",
            sizeLabel = "9.0 GB",
            paramCount = "14B",
            quantization = "Q4_K_M",
            ramRequired = "12 GB+",
            category = ModelCategory.CODING,
            capabilities = listOf("Autonomous agent", "Complex refactoring", "Architecture planning", "Self-correction", "128K context"),
            url = "https://huggingface.co/Qwen/Qwen2.5-Coder-14B-Instruct-GGUF/resolve/main/qwen2.5-coder-14b-instruct-q4_k_m.gguf",
            filename = "qwen2.5-coder-14b-instruct-q4_k_m.gguf"
        ),

        // ── Multimodal (Vision + Text) ──────────────────────────────

        RecommendedModel(
            name = "Qwen3-VL 2B",
            description = "Compact vision model. Screenshots + code analysis.",
            detailedDescription = "Alibaba's latest multimodal model — sees images and understands text. " +
                "Can analyze screenshots, UI mockups, diagrams, error popups, and code on screen. " +
                "Use case: photograph a whiteboard sketch → generate code. " +
                "Photo of an error → get fix suggestion. Screenshot of UI → get layout code. " +
                "Compact 2B fits on most devices. " +
                "Note: requires mmproj vision file (downloaded automatically). " +
                "Vision features available in a future app update; text-only mode works now.",
            sizeLabel = "1.8 GB + 1.2 GB vision",
            paramCount = "2B",
            quantization = "Q4_K_M",
            ramRequired = "4 GB+",
            category = ModelCategory.MULTIMODAL,
            capabilities = listOf("Image analysis", "Screenshot reading", "UI-to-code", "Error diagnosis"),
            url = "https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main/Qwen3VL-2B-Instruct-Q4_K_M.gguf",
            filename = "Qwen3VL-2B-Instruct-Q4_K_M.gguf",
            mmProjUrl = "https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main/mmproj-Qwen3VL-2B-Instruct-F16.gguf",
            mmProjFilename = "mmproj-Qwen3VL-2B-Instruct-F16.gguf"
        ),

        RecommendedModel(
            name = "Qwen3-VL 4B",
            description = "Best compact vision model. Image + code + reasoning.",
            detailedDescription = "The most powerful compact multimodal model from Alibaba. " +
                "Understands images, charts, tables, handwritten text, and code screenshots. " +
                "Can extract structured data from photos of invoices, forms, and documents. " +
                "Video understanding: analyze screen recordings of bugs or UI flows. " +
                "Significantly better reasoning than 2B — handles multi-step visual tasks. " +
                "Note: requires mmproj vision file (downloaded automatically). " +
                "Vision features available in a future app update; text-only mode works now.",
            sizeLabel = "2.9 GB + 1.2 GB vision",
            paramCount = "4B",
            quantization = "Q4_K_M",
            ramRequired = "6 GB+",
            category = ModelCategory.MULTIMODAL,
            capabilities = listOf("Image analysis", "Chart/table reading", "OCR", "Video understanding", "UI analysis"),
            url = "https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/Qwen3VL-4B-Instruct-Q4_K_M.gguf",
            filename = "Qwen3VL-4B-Instruct-Q4_K_M.gguf",
            mmProjUrl = "https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/mmproj-Qwen3VL-4B-Instruct-F16.gguf",
            mmProjFilename = "mmproj-Qwen3VL-4B-Instruct-F16.gguf"
        ),

        RecommendedModel(
            name = "Qwen2.5-VL 7B",
            description = "Full-featured vision model. Agents, bounding boxes, video.",
            detailedDescription = "Alibaba's flagship 7B vision-language model. " +
                "Recognizes objects, reads text in images (OCR), understands charts and tables. " +
                "Can act as a visual agent — reason about UI and direct tool use. " +
                "Generates bounding boxes and spatial coordinates for objects in images. " +
                "Processes videos over 1 hour — pinpoints relevant segments. " +
                "Best multimodal experience if your device has enough RAM. " +
                "Note: requires mmproj vision file (downloaded automatically). " +
                "Vision features available in a future app update; text-only mode works now.",
            sizeLabel = "4.9 GB + 1.5 GB vision",
            paramCount = "7B",
            quantization = "Q4_K_M",
            ramRequired = "8 GB+",
            category = ModelCategory.MULTIMODAL,
            capabilities = listOf("Visual agent", "Object detection", "OCR", "Video analysis", "Bounding boxes", "1hr+ video"),
            url = "https://huggingface.co/Mungert/Qwen2.5-VL-7B-Instruct-GGUF/resolve/main/Qwen2.5-VL-7B-Instruct-Q4_K_M.gguf",
            filename = "Qwen2.5-VL-7B-Instruct-Q4_K_M.gguf",
            mmProjUrl = "https://huggingface.co/Mungert/Qwen2.5-VL-7B-Instruct-GGUF/resolve/main/Qwen2.5-VL-7B-Instruct-mmproj-f16.gguf",
            mmProjFilename = "Qwen2.5-VL-7B-Instruct-mmproj-f16.gguf"
        )
    )

    /** Models grouped by category for sectioned display. */
    val byCategory: Map<ModelCategory, List<RecommendedModel>>
        get() = list.groupBy { it.category }
}
