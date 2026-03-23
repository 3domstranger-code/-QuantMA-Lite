package com.quantma.lite.data.repository

import com.quantma.lite.domain.repository.FileRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileRepositoryImpl @Inject constructor() : FileRepository {

    override fun listDirectory(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    override fun readFile(path: String): String {
        val file = File(path)
        if (!file.exists() || !file.isFile) return ""
        return file.readText()
    }

    override fun writeFile(path: String, content: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    override fun deleteFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    override fun copyFile(sourcePath: String, destPath: String): Boolean {
        val source = File(sourcePath)
        if (!source.exists()) return false
        val destDir = File(destPath)
        val target = File(destDir, source.name)
        if (target.exists()) return false
        return try {
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = false)
            } else {
                source.copyTo(target, overwrite = false)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun moveFile(sourcePath: String, destPath: String): Boolean {
        val source = File(sourcePath)
        if (!source.exists()) return false
        val destDir = File(destPath)
        val target = File(destDir, source.name)
        if (target.exists()) return false
        return try {
            if (source.renameTo(target)) {
                true
            } else {
                // Fallback: copy + delete (cross-filesystem)
                val copied = if (source.isDirectory) {
                    source.copyRecursively(target, overwrite = false)
                } else {
                    source.copyTo(target, overwrite = false)
                    true
                }
                if (copied) {
                    if (source.isDirectory) source.deleteRecursively() else source.delete()
                } else false
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun renameFile(path: String, newName: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val target = File(file.parentFile, newName)
        if (target.exists()) return false
        return file.renameTo(target)
    }

    override fun createDirectory(path: String): Boolean {
        val dir = File(path)
        if (dir.exists()) return false
        return dir.mkdirs()
    }

    override fun exists(path: String): Boolean = File(path).exists()

    override fun isDirectory(path: String): Boolean = File(path).isDirectory
}
