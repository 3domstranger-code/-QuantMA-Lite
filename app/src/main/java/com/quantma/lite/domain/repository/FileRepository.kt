package com.quantma.lite.domain.repository

import java.io.File

interface FileRepository {
    fun listDirectory(path: String): List<File>
    fun readFile(path: String): String
    fun writeFile(path: String, content: String)
    fun deleteFile(path: String): Boolean
    fun copyFile(sourcePath: String, destPath: String): Boolean
    fun moveFile(sourcePath: String, destPath: String): Boolean
    fun renameFile(path: String, newName: String): Boolean
    fun createDirectory(path: String): Boolean
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
}
