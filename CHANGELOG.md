# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

---

## [Unreleased]

---

## [1.1.0-lite] - 2026-03-23

### Added
- Background inference — AI generation runs off the UI thread; app stays responsive during output
- Question chips — suggested follow-up prompts displayed below AI responses
- Live CPU/GPU load display in the top bar, updating in real time during inference
- In-app editor for Skills, Rules & Hooks — all three agent extension types editable directly from the app
- Extended color palette — 6 accent/bubble/panel color options

### Fixed
- Scroll position fix — chat list no longer loses position or jumps during streaming responses

---

## [1.0.0-lite] - 2026-03-23

### Added
- Initial QuantMA Lite release — rebranded and trimmed from SafeAiCodePocketAgent (CodeAgent)
- 5 read-only agent tools: `read_file`, `list_dir`, `search_grep`, `git_status`, `git_log`
- Lite restrictions enforced at runtime:
  - Write tools (`write_file`, `create_file`, `append_file`, `delete_file`, `format_file`) are blocked
  - Git mutation tools (`git_add`, `git_commit`, `git_push`) are blocked
  - `run_command` / shell execution is blocked
- QuantMA branding (name, icon, strings) replacing CodeAgent branding
- All core inference features from CodeAgent v2.10.x: llama.cpp offline inference via JNI, GGUF model support, Room chat history, agent mode, sampling parameter controls

---

## [Initial] - 2026-03-23

- Repository initialized (`1d9aaab`)
