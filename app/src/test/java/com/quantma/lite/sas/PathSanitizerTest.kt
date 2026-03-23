package com.quantma.lite.sas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class PathSanitizerTest {

    private lateinit var sanitizer: PathSanitizer
    private lateinit var workingDir: File

    @Before
    fun setUp() {
        // Use a temp directory as the working directory for tests
        workingDir = File(System.getProperty("java.io.tmpdir"), "sas_test_workdir").also {
            it.mkdirs()
        }
        val config = SasConfig(workingDir = workingDir.absolutePath)
        sanitizer = PathSanitizer(config)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Relative Path Resolution
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `resolve simple relative path within working dir`() {
        val result = sanitizer.resolve("src/main.kt")
        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertTrue(resolved.absolutePath.contains("src"))
        assertTrue(resolved.absolutePath.contains("main.kt"))
    }

    @Test
    fun `resolve current directory dot`() {
        val result = sanitizer.resolve(".")
        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertEquals(workingDir.canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun `resolve nested path within working dir`() {
        val result = sanitizer.resolve("src/main/java/com/example/App.kt")
        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertTrue(resolved.canonicalPath.startsWith(workingDir.canonicalPath))
    }

    @Test
    fun `resolve simple filename`() {
        val result = sanitizer.resolve("README.md")
        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertTrue(resolved.canonicalPath.startsWith(workingDir.canonicalPath))
        assertTrue(resolved.name == "README.md")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Path Traversal Rejection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `reject path with dotdot traversal`() {
        val result = sanitizer.resolve("../etc/passwd")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception!!.message!!.contains("traversal"))
    }

    @Test
    fun `reject path with dotdot in middle`() {
        val result = sanitizer.resolve("src/../../secret.txt")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `reject path with dotdot at start`() {
        val result = sanitizer.resolve("../..")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun `reject path with backslash dotdot traversal`() {
        val result = sanitizer.resolve("..\\etc\\passwd")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Absolute Path Rejection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `reject absolute path starting with slash`() {
        val result = sanitizer.resolve("/etc/passwd")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception!!.message!!.contains("Absolute paths"))
    }

    @Test
    fun `reject absolute path starting with backslash`() {
        val result = sanitizer.resolve("\\system\\bin")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Blocked System Prefixes
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `blocked prefixes are configured by default`() {
        val config = SasConfig(workingDir = workingDir.absolutePath)
        assertTrue(config.blockedPrefixes.contains("/system"))
        assertTrue(config.blockedPrefixes.contains("/data/data"))
        assertTrue(config.blockedPrefixes.contains("/proc"))
        assertTrue(config.blockedPrefixes.contains("/dev"))
        assertTrue(config.blockedPrefixes.contains("/etc"))
        assertTrue(config.blockedPrefixes.contains("/sbin"))
        assertTrue(config.blockedPrefixes.contains("/vendor"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Empty / Blank Paths
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `reject empty path`() {
        val result = sanitizer.resolve("")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception!!.message!!.contains("empty"))
    }

    @Test
    fun `reject blank path`() {
        val result = sanitizer.resolve("   ")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Extension Validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `isExtensionAllowed returns true for allowed extension`() {
        val file = File(workingDir, "test.kt")
        assertTrue(sanitizer.isExtensionAllowed(file))
    }

    @Test
    fun `isExtensionAllowed returns true for files without extension`() {
        // Files without extension (e.g., Makefile) are always allowed
        val file = File(workingDir, "Makefile")
        assertTrue(sanitizer.isExtensionAllowed(file))
    }

    @Test
    fun `isExtensionAllowed returns false for disallowed extension`() {
        val file = File(workingDir, "malware.exe")
        assertFalse(sanitizer.isExtensionAllowed(file))
    }

    @Test
    fun `isExtensionAllowed returns true when whitelist is empty`() {
        val config = SasConfig(workingDir = workingDir.absolutePath, allowedExtensions = emptySet())
        val permissiveSanitizer = PathSanitizer(config)
        val file = File(workingDir, "anything.xyz")
        assertTrue(permissiveSanitizer.isExtensionAllowed(file))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Write Validation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `validateForWrite rejects disallowed extension`() {
        val file = File(workingDir, "script.exe")
        val result = sanitizer.validateForWrite(file)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("extension"))
    }

    @Test
    fun `validateForWrite allows known extension`() {
        val file = File(workingDir, "Main.kt")
        val result = sanitizer.validateForWrite(file)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `validateForWrite allows file without extension`() {
        val file = File(workingDir, "Dockerfile")
        val result = sanitizer.validateForWrite(file)
        assertTrue(result.isSuccess)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Relative Path Display
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `relativePath returns dot for working dir itself`() {
        val result = sanitizer.relativePath(workingDir)
        assertEquals(".", result)
    }

    @Test
    fun `relativePath returns relative from working dir`() {
        val file = File(workingDir, "src/main.kt")
        val relative = sanitizer.relativePath(file)
        assertTrue(relative.contains("src"))
        assertTrue(relative.contains("main.kt"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Path Depth
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `reject path exceeding max depth`() {
        val config = SasConfig(workingDir = workingDir.absolutePath, maxPathDepth = 3)
        val shallowSanitizer = PathSanitizer(config)
        val deepPath = "a/b/c/d/e.kt" // depth 5
        val result = shallowSanitizer.resolve(deepPath)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("depth"))
    }

    @Test
    fun `accept path within max depth`() {
        val config = SasConfig(workingDir = workingDir.absolutePath, maxPathDepth = 10)
        val normalSanitizer = PathSanitizer(config)
        val result = normalSanitizer.resolve("src/main.kt")
        assertTrue(result.isSuccess)
    }
}
