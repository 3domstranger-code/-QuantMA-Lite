# CodeAgent User Guide

## What is CodeAgent?

CodeAgent is an AI assistant that runs **entirely on your phone**. It uses a local language model (llama.cpp) to help with code, answer questions, and provide advice — without sending any data to external servers.

**Two modes:**
- **Chat Mode** — a general-purpose AI assistant for Q&A, advice, learning, code explanation, translation, and everyday tasks. Fast and simple.
- **Agent Mode** — an autonomous coding agent that reads, writes, and edits files, manages git repositories, and runs code quality checks.

---

## Getting Started

### First Launch

1. **Download a model** — on first launch you'll be prompted to download an AI model from the catalog. Choose one based on your needs:

   **For everyday chat & advice (Chat Mode):**
   - **Qwen2.5 3B** (~2 GB, 4 GB+ RAM) — compact, multilingual, fast on any device
   - **Qwen2.5 7B** (~4.7 GB, 6 GB+ RAM) — best general chat for flagship phones
   - **Phi-3.5 Mini** (~2.2 GB, 4 GB+ RAM) — fast reasoning from Microsoft

   **For coding & agent work (Agent Mode):**
   - **Qwen2.5-Coder 3B** (~2.1 GB, 4 GB+ RAM) — best compact coder
   - **Qwen2.5-Coder 7B** (~4.7 GB, 6 GB+ RAM) — recommended for most users
   - **Qwen2.5-Coder 14B** (~9 GB, 12 GB+ RAM) — near GPT-4o, flagship devices only

   **For image understanding (multimodal):**
   - **Qwen3-VL 2B** (~1.8 GB + vision) — screenshots, UI mockups, error photos
   - **Qwen3-VL 4B** (~2.9 GB + vision) — charts, tables, OCR, video analysis

   The catalog shows detailed specs and capabilities before download, with Download/Cancel buttons.

2. **Grant storage permission** — CodeAgent needs access to your files to browse and edit code.

3. **Set working directory** — go to Settings and set the folder where your projects live.

### Navigation

The app has 3 main tabs:
- **Chat** — talk to the AI agent
- **Files** — browse and edit files
- **Settings** — configure the agent, models, git, and appearance

---

## Chat Mode: AI Assistant

In Chat Mode (default), CodeAgent works as a fast, general-purpose AI assistant. No file access — just conversation.

**What you can do:**
- Ask questions about code, programming, and architecture
- Get advice, explanations, and learning help
- Translate text between languages
- Brainstorm ideas, write drafts, summarize content
- Discuss anything — the model runs locally and privately

**Switching modes:** Tap the robot icon in the top bar to toggle between Chat Mode and Agent Mode. If you ask for file/git operations in Chat Mode, you'll be prompted to switch.

**Prompt format auto-detection:** The app automatically detects your model type (Qwen, Llama 3, CodeLlama, Phi, DeepSeek, etc.) and applies the correct prompt template. No manual configuration needed.

---

## Agent Mode: Autonomous Coding

The agent is powered by **SAS (Secure Agent Shell) v3.2** — a sandboxed execution engine with **26 tools**, intent detection, and two execution modes.

### How the Agent Works

When you send a message in Agent Mode, the agent enters a **think-act-observe loop**:

1. **Think** — the AI analyzes your request and detects intent (one of 14 categories)
2. **Act** — it executes a tool (read file, write file, search, git command, etc.)
3. **Observe** — it sees the result and decides what to do next
4. Repeat until the task is done (up to 15 rounds by default)

### Two Execution Modes

| Mode | For models | How it works |
|------|-----------|-------------|
| **Standard** | Powerful (14B+, Qwen 32B+) | Full agent loop — the LLM plans autonomously |
| **Guided** | Small (7B, 3B) | TaskPlanner breaks the request into micro-steps, each with a focused prompt for a single tool |

Guided Mode activates automatically based on model size. The planner detects your intent and generates a step-by-step plan, so even small models can complete complex tasks like "create a file and commit it".

### What You Can Ask

#### File Operations (6 tools)
- "Read src/Main.kt" — read with optional offset/limit
- "Create a new file called Utils.kt with a formatDate function"
- "Delete the old test file" — moves to `.sas_trash/`, not permanent
- "Show me all files in the src directory" — recursive listing available
- "Add error handling to the processData function in DataManager.kt"
- "Append a license header to all source files"

#### Code Analysis (2 tools)
- "Find all TODO comments in the project" — groups by tag (TODO/FIXME/HACK/XXX)
- "How many lines of code are in src/?" — breakdown: total, code, blank, comments
- "Search for all usages of fetchData" — regex search across the project

#### Refactoring
- "Refactor the UserService class to use coroutines"
- "Rename the function getData to fetchUserData"
- "Extract the validation logic into a separate function"

#### Git Operations (13 tools)
- "Show git status" / "Show detailed git status" — staged/modified/untracked + ahead/behind
- "Commit all changes with a descriptive message" / "Auto-commit" — auto-generates message from diff
- "Create a new branch called feature/auth"
- "Switch to the main branch" / "Checkout develop"
- "Push changes to remote" — safe push, blocks if behind remote
- "Show commit history" — with optional path filter
- "Stash my current changes" / "Pop stash" / "List stashes"
- "Merge feature/auth into main" / "Delete branch old-feature"

#### Code Quality (4 tools)
- "Lint this file" — trailing whitespace, line length, naming, empty catch, wildcard imports
- "Format Main.kt" — auto-fix indentation, line endings, EOF newline (shows diff preview)
- "Count lines of code in the project"
- "Find all TODOs in the project"

#### Composite Operations
- "Create Utils.kt and commit it" — the agent creates the file, lints it, stages, and commits in one flow
- "Verify Main.kt" — read-only analysis: read + lint + count lines + find TODOs + LLM summary

### Approval System

When the agent wants to perform a **destructive operation**, it will ask for your approval first:

**Operations that require approval:**
- Writing/creating/appending to files
- Deleting files (soft delete to `.sas_trash/`)
- Formatting files
- Git: add, commit, push, create/delete branch, checkout, merge

You'll see a preview of what will change (a diff for file edits) and can **Approve** or **Reject** the action.

### Security

The agent runs in a **sandboxed environment**:
- All paths are locked to your working directory — no escape possible
- Path traversal (`..`) and symlinks are blocked
- Only whitelisted file extensions (~30 types) can be written
- System directories (`/system`, `/proc`, `/dev`, etc.) are blocked
- Code is validated before writing: bracket matching, string literals, content loss detection, dangerous pattern scanning (`eval`, `exec`, `subprocess`)
- Rate limiting: max 60 tool operations per minute
- Deleted files go to `.sas_trash/`, not permanently removed

### Skills (Smart Shortcuts)

The agent automatically detects certain intents and activates specialized skills:

| What you say | Skill activated |
|---|---|
| "Explain this code" / "Объясни код" | Code explanation — reads the file and provides a detailed breakdown |
| "Refactor..." / "Отрефактори..." | Refactoring — analyzes, suggests a plan, then applies changes |
| "Write tests" / "Напиши тесты" | Test generation — reads source, creates test class |
| "Commit" / "Закоммить" | Git commit — checks status, stages files, commits with auto-message |
| "Find bugs" / "Найди баги" | Bug finder — analyzes code and lists issues with file:line references |
| "Add feature" / "Добавь" | Feature addition — reads relevant files, writes new code |
| "Project overview" / "Расскажи про проект" | Project summary — scans project structure, summarizes architecture |
| "Create and commit" / "Создай и закоммить" | Composite — create file + lint + git add + commit + verify |
| "Verify file" / "Проверь файл" | Read-only analysis — read + lint + count + find TODO + summary |

### Tips for Better Results

1. **Be specific** — "Add input validation to the login function in AuthService.kt" works better than "fix the code"
2. **One task at a time** — the agent handles focused tasks best
3. **Set the working directory** — make sure it points to your project root
4. **Read before edit** — if you want to modify a file, mention it by name so the agent reads it first
5. **Review approvals** — always check the diff preview before approving writes
6. **Use composite commands** — "create and commit" saves you multiple back-and-forth steps
7. **Small models?** — Guided Mode kicks in automatically, no extra setup needed

---

## Files: Browsing and Editing

### File Browser

The file browser provides full file management without leaving the app.

**Navigation shortcuts:**
- **Home chip** — tap "Home" to jump back to your working directory root from any subdirectory
- **Downloads shortcut** — tap "Downloads" to jump directly to the device Downloads folder (useful for moving model files or received documents into your project)

**Opening files:**
- Tap any file to open it in the code editor
- Tap a directory to navigate into it
- Git repositories show a "Git" badge — tap it to open git management

**Context menu (long-press):**
Long-press any file or folder to open the action sheet:

| Action | What it does |
|--------|-------------|
| **Copy** | Marks the file/folder for copying |
| **Cut** | Marks the file/folder for moving |
| **Rename** | Opens a dialog to rename the item in place |
| **Delete** | Deletes the file or recursively deletes the folder (with confirmation) |
| **Info** | Shows file path, size, and last-modified date |

**Copy / Cut / Paste workflow:**
1. Long-press a file or folder and tap **Copy** (or **Cut**)
2. Navigate to the destination directory
3. Tap the **Paste** button (floating action button at the bottom of the screen)
4. The file is copied (or moved) to the current directory

**Creating a new folder:**
- Tap the **New Folder** button in the toolbar
- Enter a name and confirm — the directory is created immediately

### Code Editor
- Syntax highlighting (powered by Sora Editor + TextMate grammars)
- Supports 30+ languages: Kotlin, Java, Python, JavaScript, TypeScript, C/C++, Go, Rust, Swift, and more
- Search and replace
- Line numbers
- Auto-indentation

---

## Git Integration

### Setting Up Git

1. Go to **Settings > Git Credentials**
2. Enter your username and Personal Access Token (PAT)
3. Credentials are encrypted with AES-256 and protected by biometric authentication

### Git Features

From the **Files** tab, tap the "Git" badge on any repository to open the git screen:

- **Status** — see modified, staged, and untracked files
- **Diff** — view changes for each modified file
- **Stage/Unstage** — select which files to commit
- **Commit** — write a commit message and commit
- **Branches** — create, switch, delete branches
- **Pull/Push** — sync with remote
- **Log** — view commit history

You can also use git through the **Chat** — just ask the agent.

### Git Through Chat

The agent supports advanced git operations:

```
"Show detailed git status"     → staged, modified, untracked sections + ahead/behind info
"Auto-commit"                  → generates commit message from diff automatically
"Push to remote"               → checks ahead/behind before pushing (safe push)
"Stash my changes"             → saves working directory state
"Pop stash"                    → restores stashed changes
"Merge feature into main"      → merges branches (requires approval)
```

---

## Settings

### Model Settings
- **Select model** — choose which AI model to use
- **Download models** — browse and download models from the catalog (14 models across 4 categories: Compact, Code, General, Multimodal)

### Inference Settings (Auto/Manual)

Every inference parameter has an **Auto/Manual** toggle. Auto mode detects your device's hardware and selects optimal values automatically.

- **Threads** — CPU threads for inference (Auto: uses performance cores − 1)
- **Context Size** — token window (Auto: scaled by RAM and model size, 512–8192)
- **Batch Size** (Advanced) — prompt processing batch (Auto: scaled by RAM, 256–2048)
- **Flash Attention** (Advanced) — faster attention computation (Auto: on for ctx ≥ 2048)
- **Lock in RAM** (Advanced) — prevents model paging to disk (Auto: on if RAM ≥ 8GB)

### Sampling Settings (Auto/Manual, Advanced Mode)

- **Temperature** — creativity level (Auto: 0.7)
- **Top-P** — nucleus sampling (Auto: 0.95)
- **Top-K** — limits to top K tokens (Auto: 40)
- **Min-P** — modern probability filter (Auto: 0.05)
- **Repeat Penalty** — penalizes repetition (Auto: 1.1)
- **Penalty Window** — how many recent tokens to check (Auto: 64)

### GPU Acceleration (Advanced Mode)
- **Use Vulkan GPU** — offload computation to GPU
- **GPU Layers** — how many model layers on GPU (Auto: based on VRAM)

### Agent Settings
- **Working directory** — the root folder for the agent's file operations
- **Max rounds** — how many think-act-observe cycles the agent can do per request (default: 15)
- **Agent configuration** — advanced prompt settings

### Git Settings
- **Git credentials** — manage your PAT for push/pull operations

### Appearance
- **Theme** — light, dark, AMOLED, or system default
- **Language** — English or Russian
- **Custom colors** — user/assistant bubbles, accent, panel colors

### About
- **Version** — current app version
- **Licenses** — open source libraries used
- **Privacy Policy** — how your data is handled
- **Terms of Service** — usage terms

---

## Security

### What's Protected

- **All files stay on your device** — the AI model runs locally, no cloud processing
- **Git credentials encrypted** — AES-256 via Android EncryptedSharedPreferences
- **Biometric protection** — fingerprint/face/PIN for credential access
- **Path sandboxing** — the agent cannot access system directories (/system, /proc, /dev, etc.)
- **Approval for destructive ops** — file writes, deletes, git push/merge all require your confirmation
- **File history** — snapshots saved before every write (undo available)
- **Soft deletes** — deleted files go to `.sas_trash/`, not permanently removed

### What's NOT Protected

- **Git push sends data** — when you push, your code goes to the remote server YOU specify
- **AI can make mistakes** — always review generated code before approving
- **No backup guarantee** — while file history exists, maintain your own backups for critical data

---

## Troubleshooting

### Agent Not Responding
- Check that a model is downloaded and selected
- Try reducing max rounds in settings
- Restart the app

### Model Too Slow
- Try a smaller model (3B instead of 7B, or use Q4_K_M quantization)
- Close other apps to free RAM
- The first response is always slower (model loading)
- For Chat Mode, general-purpose models (Qwen2.5 3B) are faster than code-specialized ones

### Git Push Fails
- Verify your PAT has the correct permissions (repo scope)
- Check network connectivity
- Make sure credentials are entered in Settings > Git Credentials

### "Permission Denied" for Files
- Grant storage permission in Android settings
- Make sure the working directory path is correct
- The agent cannot access system directories by design

---

## Keyboard Shortcuts (when using external keyboard)

The code editor supports standard keyboard shortcuts when an external keyboard is connected.

---

## Data & Privacy

- No analytics, no tracking, no telemetry
- AI runs 100% on device
- Data only leaves your phone when YOU initiate a git push/pull
- See full [Privacy Policy](PRIVACY_POLICY.md) for details
