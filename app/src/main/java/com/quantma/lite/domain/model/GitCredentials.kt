package com.quantma.lite.domain.model

/**
 * Git credentials for remote operations (Phase 4 v1.3.0).
 */
data class GitCredentials(
    val token: String = "",
    val authorName: String = "",
    val authorEmail: String = ""
)

/**
 * Represents a configured Git remote.
 */
data class GitRemote(
    val name: String,
    val url: String
)
