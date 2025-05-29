package utils

import FileSystemItem
import java.io.File

fun listFiles(path: String): List<FileSystemItem> {
    val dir = File(path)
    return if (dir.exists() && dir.isDirectory) {
        dir.listFiles()?.map {
            FileSystemItem(it.name, it.absolutePath, it.isDirectory)
        } ?: emptyList()
    } else {
        emptyList()
    }
}

fun getRootDirectories(): List<FileSystemItem> {
    return File.listRoots().map {
        FileSystemItem(it.name, it.absolutePath, true)
    }
}

fun renameFile(oldPath: String, newPath: String): Boolean {
    val oldFile = File(oldPath)
    val newFile = File(newPath)
    return oldFile.renameTo(newFile)
}

fun copyFile(src: String, dst: String): Boolean {
    val source = File(src)
    val target = File(dst)
    return try {
        source.copyRecursively(target, overwrite = true)
        true
    } catch (e: Exception) {
        false
    }
}

fun moveFile(src: String, dst: String): Boolean {
    val result = copyFile(src, dst)
    if (result) {
        File(src).delete()
    }
    return result
}
