# QuantMA Lite

Local AI coding assistant for Android. Run LLMs fully offline on your device.

**Квант** (Russian) / **QuantMA** (English) — Quantum Mobile Agent.

## What is it

QuantMA Lite runs a language model entirely on your phone via [llama.cpp](https://github.com/ggml-org/llama.cpp). No servers, no API keys, no data leaving your device.

Two modes:
- **Chat Mode** — general AI assistant: questions, code explanations, translations, advice
- **Agent Mode** — reads files, searches code, shows git status — all locally

## Features

- Fully offline inference (llama.cpp via JNI, ARM64 NEON + KleidiAI)
- Model catalog with 14 models (1.5B to 14B parameters)
- Auto prompt format detection (ChatML, Llama 3, CodeLlama)
- Agent with 7 read-only tools: `read_file`, `list_files`, `search_files`, `git_status`, `git_diff`, `git_branch`, `git_stash_list`
- Code editor with syntax highlighting (Sora Editor, 17 languages)
- File manager with context menu (copy, cut, rename, delete, info)
- Git integration (JGit): status, diff, log, branch list
- Dark/light themes (Default, Dracula, Monokai, Nord)
- Localization: English + Russian
- Material 3 with Jetpack Compose

## Lite vs Ultra

| Feature | Lite | Ultra |
|---------|------|-------|
| Chat mode | Yes | Yes |
| Agent read tools (7) | Yes | Yes |
| Agent write tools (13+) | No | Yes |
| Task planning (auto-decomposition) | No | Yes |
| Model router (auto-select best model) | No | Yes |
| RAG search (TF-IDF + BM25) | No | Yes |
| Guided mode (step-by-step agent) | No | Yes |
| License | Apache 2.0 | Proprietary |

## Requirements

| Requirement | Minimum |
|---|---|
| Android | 12 (API 31) |
| ABI | arm64-v8a |
| RAM | 4 GB+ |
| Storage | ~2 GB for model file |

## Recommended Models

| Use case | Model | Size | RAM |
|---|---|---|---|
| Chat | Qwen2.5 3B Instruct | ~2 GB | 4 GB |
| Code | Qwen2.5-Coder 7B Q4_K_M | ~4.7 GB | 6 GB |
| Vision | Qwen3-VL 4B | ~2.9 GB | 6 GB |

## Build

Prerequisites: JDK 21, Android SDK (compileSdk 35), NDK 29, CMake 4.1.2, Gradle 9.3.1.

```bash
cd QuantMA-Lite
export JAVA_HOME="/path/to/android-studio/jbr"
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## License

Apache License 2.0 — see [LICENSE](LICENSE).

## Credits

- [llama.cpp](https://github.com/ggml-org/llama.cpp) (MIT) — LLM inference engine
- [JGit](https://www.eclipse.org/jgit/) (Eclipse Distribution License) — Git operations
- [Sora Editor](https://github.com/niceedit/sora-editor) (LGPL-2.1) — Code editor
- [java-diff-utils](https://github.com/java-diff-utils/java-diff-utils) (Apache 2.0) — Diff engine
- Jetpack Compose, Hilt, Room (Apache 2.0) — Android framework
- [Timber](https://github.com/JakeWharton/timber) (Apache 2.0) — Logging

## Feedback

This is an early release. We're collecting feedback:
- What tasks would you use a local AI agent for on your phone?
- What distribution model works for you? (open source, one-time purchase, subscription)

Open an issue or comment on the Habr announcement.
