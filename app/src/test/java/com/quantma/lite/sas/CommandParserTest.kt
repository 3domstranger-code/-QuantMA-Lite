package com.quantma.lite.sas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandParserTest {

    private lateinit var parser: CommandParser

    @Before
    fun setUp() {
        parser = CommandParser()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // JSON Protocol
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `parse valid JSON tool command`() {
        val input = """{"tool": "read_file", "args": {"path": "test.kt"}}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.READ_FILE, cmd!!.tool)
        assertEquals("test.kt", cmd.stringArg("path"))
    }

    @Test
    fun `parse JSON with thought field extracts thought`() {
        val input = """{"tool": "read_file", "args": {"path": "src/main.kt"}, "thought": "I need to check the file first"}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.READ_FILE, cmd!!.tool)
        assertEquals("I need to check the file first", cmd.thought)
        assertEquals("src/main.kt", cmd.stringArg("path"))
    }

    @Test
    fun `parse JSON in markdown code block`() {
        val input = """
            Here is my command:
            ```json
            {"tool": "list_dir", "args": {"path": "src"}}
            ```
        """.trimIndent()
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.LIST_DIR, cmd!!.tool)
        assertEquals("src", cmd.stringArg("path"))
    }

    @Test
    fun `parse JSON with done tool sets isDone`() {
        val input = """{"tool": "done"}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.DONE, cmd!!.tool)
    }

    @Test
    fun `parse JSON with id field preserves id`() {
        val input = """{"id": "abc-123", "tool": "read_file", "args": {"path": "test.kt"}}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals("abc-123", cmd!!.id)
    }

    @Test
    fun `parse JSON generates id when not provided`() {
        val input = """{"tool": "read_file", "args": {"path": "test.kt"}}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertTrue(cmd!!.id.isNotEmpty())
    }

    @Test
    fun `parse JSON with write_file and content`() {
        val input = """{"tool": "write_file", "args": {"path": "hello.py", "content": "print('hello')\n"}}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.WRITE_FILE, cmd!!.tool)
        assertEquals("hello.py", cmd.stringArg("path"))
        assertEquals("print('hello')\n", cmd.stringArg("content"))
    }

    @Test
    fun `parse JSON with nested args object`() {
        val input = """{"tool": "search_grep", "args": {"pattern": "TODO", "path": "src"}}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.SEARCH_GREP, cmd!!.tool)
        assertEquals("TODO", cmd.stringArg("pattern"))
        assertEquals("src", cmd.stringArg("path"))
    }

    @Test
    fun `parse JSON embedded in surrounding text`() {
        val input = """I'll read the file now. {"tool": "read_file", "args": {"path": "main.kt"}} That should work."""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.READ_FILE, cmd!!.tool)
        assertEquals("main.kt", cmd.stringArg("path"))
    }

    @Test
    fun `parse JSON with trailing comma is tolerated`() {
        val input = """{"tool": "read_file", "args": {"path": "test.kt",},}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.READ_FILE, cmd!!.tool)
    }

    @Test
    fun `parse JSON with empty args`() {
        val input = """{"tool": "git_status", "args": {}}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.GIT_STATUS, cmd!!.tool)
        assertTrue(cmd.args.isEmpty())
    }

    @Test
    fun `parse JSON without args field defaults to empty map`() {
        val input = """{"tool": "done"}"""
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertTrue(cmd!!.args.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ACTION Protocol
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `parse ACTION protocol read_file`() {
        val input = "ACTION: read_file test.kt"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.READ_FILE, cmd!!.tool)
        assertEquals("test.kt", cmd.stringArg("path"))
    }

    @Test
    fun `parse ACTION protocol list_dir`() {
        val input = "ACTION: list_dir src/main"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.LIST_DIR, cmd!!.tool)
        assertEquals("src/main", cmd.stringArg("path"))
    }

    @Test
    fun `parse ACTION with CONTENT and END_CONTENT markers`() {
        val input = """
            ACTION: write_file hello.py
            CONTENT:
            print("hello world")
            END_CONTENT
        """.trimIndent()
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.WRITE_FILE, cmd!!.tool)
        assertEquals("hello.py", cmd.stringArg("path"))
        assertEquals("print(\"hello world\")", cmd.stringArg("content"))
    }

    @Test
    fun `parse ACTION search_grep splits pattern and path`() {
        val input = "ACTION: search_grep TODO src"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.SEARCH_GREP, cmd!!.tool)
        assertEquals("TODO", cmd.stringArg("pattern"))
        assertEquals("src", cmd.stringArg("path"))
    }

    @Test
    fun `parse ACTION git_commit splits path and message`() {
        val input = "ACTION: git_commit . Add utility functions"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.GIT_COMMIT, cmd!!.tool)
        assertEquals(".", cmd.stringArg("path"))
        assertEquals("Add utility functions", cmd.stringArg("message"))
    }

    @Test
    fun `parse DONE keyword returns done command`() {
        val input = "DONE"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.DONE, cmd!!.tool)
    }

    @Test
    fun `parse DONE is case insensitive`() {
        val input = "done"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.DONE, cmd!!.tool)
    }

    @Test
    fun `parse ACTION is case insensitive on prefix`() {
        val input = "action: read_file test.kt"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.READ_FILE, cmd!!.tool)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun `parse empty string returns null`() {
        val result = parser.parse("")
        assertNull(result)
    }

    @Test
    fun `parse blank string returns null`() {
        val result = parser.parse("   \n\t  ")
        assertNull(result)
    }

    @Test
    fun `parse regular text without command returns null`() {
        val result = parser.parse("This is just a chat message without any tool call.")
        assertNull(result)
    }

    @Test
    fun `parse invalid JSON falls back to ACTION or null`() {
        val input = """{"tool": "read_file", "args": BROKEN}"""
        val result = parser.parse(input)
        // Invalid JSON should not parse successfully via JSON path
        // and there is no ACTION: prefix, so result should be null
        assertNull(result)
    }

    @Test
    fun `parse unknown tool name in JSON returns null`() {
        val input = """{"tool": "nonexistent_tool", "args": {"path": "test.kt"}}"""
        val result = parser.parse(input)
        assertNull(result)
    }

    @Test
    fun `parse unknown tool name in ACTION returns null`() {
        val input = "ACTION: nonexistent_tool test.kt"
        val result = parser.parse(input)
        assertNull(result)
    }

    @Test
    fun `parse JSON missing tool field returns null`() {
        val input = """{"args": {"path": "test.kt"}}"""
        val result = parser.parse(input)
        assertNull(result)
    }

    @Test
    fun `parse git_stash ACTION parses operation`() {
        val input = "ACTION: git_stash save WIP changes"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.GIT_STASH, cmd!!.tool)
        assertEquals("save", cmd.stringArg("operation"))
        assertEquals("WIP changes", cmd.stringArg("message"))
    }

    @Test
    fun `parse git_log ACTION with empty path defaults to dot`() {
        val input = "ACTION: git_log"
        val cmd = parser.parse(input)
        assertNotNull(cmd)
        assertEquals(ToolType.GIT_LOG, cmd!!.tool)
        assertEquals(".", cmd.stringArg("path"))
    }
}
