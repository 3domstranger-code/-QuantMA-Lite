# Privacy Policy

**Last updated:** March 2026

## Overview

CodeAgent ("the App") is a local AI coding assistant for Android. The AI model runs entirely on your device. The App does not collect, transmit, or store any personal data on external servers.

## Data Collection

**We do not collect any personal data.** The App has no analytics, no tracking, no telemetry, and no crash reporting services.

All data created and used by the App stays on your device unless you explicitly initiate a transfer (see "Data Transfers" below).

## On-Device AI

The AI model is downloaded once and runs entirely on your device. Your prompts, code, and conversations are processed locally and never leave your phone. No data is sent to any AI cloud service.

## Data Transfers Initiated by You

The App provides git integration (clone, pull, push, fetch). When you use these features, your code and git credentials are transmitted to the remote git server **you** specify (e.g., GitHub, GitLab, Bitbucket). The App does not control or monitor these transfers — they go directly from your device to the server you choose.

## File Access

The App requests the `MANAGE_EXTERNAL_STORAGE` permission to allow the AI agent to read and write files in your chosen working directory and the Downloads folder. This is required for the core functionality: code analysis, file editing, and working with external files. The App does not scan, index, or transmit your files to any external service.

## Credential Storage

Git credentials (Personal Access Tokens) are stored on-device using Android's `EncryptedSharedPreferences` with AES-256 encryption. Access to credentials is protected by biometric authentication (fingerprint, face, or device PIN). Credentials are never transmitted except when authenticating with the git server you specify.

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Git operations (clone, pull, push, fetch) to remote repositories |
| `ACCESS_NETWORK_STATE` | Check network availability before git operations |
| `MANAGE_EXTERNAL_STORAGE` | File access for AI agent and Downloads folder |

## Third-Party Services

The App does not integrate with any third-party analytics, advertising, or tracking services. The App does not contain ads.

## Children's Privacy

The App is not directed at children under 13. We do not knowingly collect any data from children.

## Changes to This Policy

We may update this Privacy Policy from time to time. Changes will be posted in the App's repository and reflected in the "Last updated" date above.

## Contact

If you have questions about this Privacy Policy, please open an issue in the project's GitHub repository.
