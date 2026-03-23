package com.quantma.lite.sas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for the dangerous-pattern scan added to [CodeValidator].
 */
class CodeValidatorDangerousPatternTest {

    private lateinit var validator: CodeValidator

    @Before
    fun setUp() {
        validator = CodeValidator()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Python
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Python eval is flagged as dangerous`() {
        val result = validator.validate(
            newContent = "result = eval(user_input)",
            fileName = "script.py"
        )
        assertTrue("eval() in Python should produce a warning", result.warnings.any { it.contains("eval") })
    }

    @Test
    fun `Python subprocess is flagged as dangerous`() {
        val result = validator.validate(
            newContent = "import subprocess\nsubprocess.run(['ls'])",
            fileName = "run.py"
        )
        assertTrue(result.warnings.any { it.contains("subprocess") })
    }

    @Test
    fun `Python os_system is flagged as dangerous`() {
        val result = validator.validate(
            newContent = "import os\nos.system('rm -rf /')",
            fileName = "bad.py"
        )
        assertTrue(result.warnings.any { it.contains("os-system") })
    }

    @Test
    fun `Python shutil_rmtree is flagged as dangerous`() {
        val result = validator.validate(
            newContent = "import shutil\nshutil.rmtree('/tmp/workdir')",
            fileName = "cleanup.py"
        )
        assertTrue(result.warnings.any { it.contains("shutil-rmtree") })
    }

    @Test
    fun `safe Python code is not flagged`() {
        val result = validator.validate(
            newContent = "def add(a, b):\n    return a + b\n",
            fileName = "math.py"
        )
        assertFalse("Safe code should not produce dangerous-pattern warnings",
            result.warnings.any { it.contains("Dangerous pattern") })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JavaScript
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `JS eval is flagged`() {
        val result = validator.validate(
            newContent = "const result = eval(code);",
            fileName = "app.js"
        )
        assertTrue(result.warnings.any { it.contains("eval") })
    }

    @Test
    fun `JS Function constructor is flagged`() {
        val result = validator.validate(
            newContent = "const fn = new Function('return 42');",
            fileName = "app.js"
        )
        assertTrue(result.warnings.any { it.contains("function-ctor") })
    }

    @Test
    fun `JS child_process require is flagged`() {
        val result = validator.validate(
            newContent = "const { exec } = require('child_process');",
            fileName = "shell.js"
        )
        assertTrue(result.warnings.any { it.contains("child-process") })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Kotlin
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `Kotlin Runtime exec is flagged`() {
        val result = validator.validate(
            newContent = "Runtime.getRuntime().exec(\"ls\")",
            fileName = "Shell.kt"
        )
        assertTrue(result.warnings.any { it.contains("runtime-exec") })
    }

    @Test
    fun `Kotlin ProcessBuilder is flagged`() {
        val result = validator.validate(
            newContent = "val pb = ProcessBuilder(listOf(\"ls\"))",
            fileName = "Run.kt"
        )
        assertTrue(result.warnings.any { it.contains("process-build") })
    }

    @Test
    fun `Kotlin deleteRecursively is flagged`() {
        val result = validator.validate(
            newContent = "File(\"/tmp\").deleteRecursively()",
            fileName = "Cleanup.kt"
        )
        assertTrue(result.warnings.any { it.contains("dangerous-del") })
    }

    @Test
    fun `safe Kotlin code is not flagged`() {
        val content = """
            package com.example

            class Calculator {
                fun add(a: Int, b: Int): Int = a + b
                fun multiply(a: Int, b: Int): Int = a * b
            }
        """.trimIndent()
        val result = validator.validate(newContent = content, fileName = "Calculator.kt")
        assertFalse(result.warnings.any { it.contains("Dangerous pattern") })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Scan-skip extensions
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `markdown files are not scanned for dangerous patterns`() {
        val result = validator.validate(
            newContent = "Run `eval(code)` and `subprocess.run(['ls'])` as examples.",
            fileName = "README.md"
        )
        assertFalse("Markdown should not trigger dangerous-pattern scan",
            result.warnings.any { it.contains("Dangerous pattern") })
    }

    @Test
    fun `json files are not scanned for dangerous patterns`() {
        val result = validator.validate(
            newContent = """{"command": "eval(x)", "process": "subprocess.run"}""",
            fileName = "config.json"
        )
        assertFalse(result.warnings.any { it.contains("Dangerous pattern") })
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Dangerous pattern scan does not block writes (warnings only)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `dangerous pattern is WARNING only — does not mark validation as invalid`() {
        val result = validator.validate(
            newContent = "eval(user_input)",
            fileName = "hack.py"
        )
        // Warnings are present but the validation should not report CRITICAL/ERROR
        assertTrue("Should have dangerous-pattern warning", result.warnings.isNotEmpty())
        assertTrue("Validation should still be valid (warnings don't block)", result.valid)
    }
}
