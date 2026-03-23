package com.quantma.lite.sas

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * Secure Agent Shell — Path Sanitizer.
 *
 * Validates and resolves all file paths to prevent directory traversal attacks,
 * access to system directories, and operations on disallowed file types.
 *
 * Every path operation in [ShellExecutor] MUST go through this sanitizer first.
 *
 * Security guarantees:
 * - All paths are resolved relative to [SasConfig.workingDir]
 * - No path can escape the working directory (.. traversal blocked)
 * - System directories (/system, /proc, /dev, etc.) are always blocked
 * - Only whitelisted file extensions are allowed for write operations
 * - File size and path depth limits enforced
 */
class PathSanitizer(private val config: SasConfig) {

    private val workingDirFile: File = File(config.workingDir)
    private val workingDirCanonical: String = workingDirFile.canonicalPath

    /**
     * Resolve a relative path against the working directory.
     *
     * @param relativePath Path from LLM (e.g. "src/main.kt", ".", "lib/utils")
     * @return Resolved [File] or failure with descriptive message
     */
    fun resolve(relativePath: String): Result<File> {
        if (relativePath.isBlank()) {
            return Result.failure(SecurityException("Path cannot be empty"))
        }

        // Block absolute paths — LLM must always use relative paths
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            return Result.failure(
                SecurityException("Absolute paths are not allowed. Use relative paths from working directory.")
            )
        }

        // Block obvious traversal patterns in the raw input
        val normalized = relativePath.replace('\\', '/')
        if (containsTraversal(normalized)) {
            return Result.failure(
                SecurityException("Path traversal detected: '..' is not allowed in paths")
            )
        }

        // Resolve against working directory
        val resolved = File(workingDirFile, normalized)
        val canonical = resolved.canonicalPath

        // Verify the canonical path is within working directory
        if (!canonical.startsWith(workingDirCanonical)) {
            return Result.failure(
                SecurityException("Path escapes working directory: resolved to $canonical")
            )
        }

        // Check against blocked system prefixes
        for (prefix in config.blockedPrefixes) {
            if (canonical.startsWith(prefix)) {
                return Result.failure(
                    SecurityException("Access to system path is blocked: $prefix")
                )
            }
        }

        // Block symbolic links — symlinks can point outside the sandbox even if the
        // canonical path appears safe. Check every component of the resolved path.
        if (isSymlinkOnPath(resolved)) {
            return Result.failure(
                SecurityException("Symbolic links are not allowed: $normalized")
            )
        }

        // Check path depth (handle both / and \ separators for cross-platform tests)
        val relativeFromRoot = canonical.removePrefix(workingDirCanonical)
            .trimStart('/', '\\')
        val depth = if (relativeFromRoot.isEmpty()) 0
            else relativeFromRoot.count { it == '/' || it == '\\' } + 1
        if (depth > config.maxPathDepth) {
            return Result.failure(
                SecurityException("Path depth ($depth) exceeds maximum (${config.maxPathDepth})")
            )
        }

        return Result.success(resolved)
    }

    /**
     * Validate a resolved file for read operations.
     * Checks existence, is-file, and size limit.
     */
    fun validateForRead(file: File): Result<Unit> {
        if (!file.exists()) {
            return Result.failure(FileNotFoundException("File not found: ${relativePath(file)}"))
        }
        if (!file.isFile) {
            return Result.failure(IllegalArgumentException("Not a file: ${relativePath(file)}"))
        }
        if (file.length() > config.maxFileSizeBytes) {
            return Result.failure(
                SecurityException(
                    "File too large: ${file.length()} bytes (max: ${config.maxFileSizeBytes})"
                )
            )
        }
        return Result.success(Unit)
    }

    /**
     * Validate a resolved file for write/create operations.
     * Checks extension whitelist and ensures parent directory is within sandbox.
     */
    fun validateForWrite(file: File): Result<Unit> {
        // Check extension whitelist (only if the set is non-empty)
        if (config.allowedExtensions.isNotEmpty()) {
            val extension = file.extension.lowercase()
            // Allow files without extension (e.g. Makefile, Dockerfile, .gitignore)
            if (extension.isNotEmpty() && extension !in config.allowedExtensions) {
                return Result.failure(
                    SecurityException(
                        "File extension '.$extension' is not allowed. " +
                            "Allowed: ${config.allowedExtensions.sorted().joinToString(", ") { ".$it" }}"
                    )
                )
            }
        }

        // Verify parent directory is also within sandbox
        val parentCanonical = (file.parentFile ?: workingDirFile).canonicalPath
        if (!parentCanonical.startsWith(workingDirCanonical)) {
            return Result.failure(
                SecurityException("Parent directory escapes working directory")
            )
        }

        return Result.success(Unit)
    }

    /**
     * Validate a resolved file for directory listing.
     * Checks existence and is-directory.
     */
    fun validateForList(file: File): Result<Unit> {
        if (!file.exists()) {
            return Result.failure(FileNotFoundException("Directory not found: ${relativePath(file)}"))
        }
        if (!file.isDirectory) {
            return Result.failure(
                IllegalArgumentException("Not a directory: ${relativePath(file)}")
            )
        }
        return Result.success(Unit)
    }

    /**
     * Validate a file path for search operations.
     * The path must be an existing directory within the sandbox.
     */
    fun validateForSearch(file: File): Result<Unit> = validateForList(file)

    /**
     * Get the relative path from working directory for display.
     */
    fun relativePath(file: File): String {
        val canonical = file.canonicalPath
        return if (canonical.startsWith(workingDirCanonical)) {
            canonical.removePrefix(workingDirCanonical).trimStart('/')
                .ifEmpty { "." }
        } else {
            file.name
        }
    }

    /**
     * Check if a file extension is in the whitelist.
     * Returns true if whitelist is empty (all extensions allowed) or extension matches.
     */
    fun isExtensionAllowed(file: File): Boolean {
        if (config.allowedExtensions.isEmpty()) return true
        val ext = file.extension.lowercase()
        return ext.isEmpty() || ext in config.allowedExtensions
    }

    // ─── Private Helpers ────────────────────────────────────────────────────

    /**
     * Check whether any component of [file]'s path from the working directory
     * downward is a symbolic link.
     *
     * Walks the path segment-by-segment so even a symlink in an intermediate
     * directory is caught, not just the final target.
     */
    private fun isSymlinkOnPath(file: File): Boolean {
        var current = file
        val root = workingDirFile.canonicalFile

        // Walk from file up to workingDir, checking each component
        val pathComponents = mutableListOf<File>()
        var walker = current.canonicalFile
        while (walker != root && walker.parentFile != null) {
            pathComponents.add(walker)
            walker = walker.parentFile!!
        }

        return pathComponents.any { component ->
            try {
                Files.isSymbolicLink(component.toPath())
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun containsTraversal(path: String): Boolean {
        val parts = path.split('/')
        return parts.any { it == ".." }
    }

    class FileNotFoundException(message: String) : Exception(message)
}
