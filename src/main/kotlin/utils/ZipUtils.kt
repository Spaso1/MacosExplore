package utils

import java.io.File

object ZipUtils {
    fun compress(files: List<File>, zipFile: File) {
        zipFile.createNewFile()
        //设置压缩后的文件名称
        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { out ->
            files.forEach { file ->
                if (file.isDirectory) {
                    addFolderToZip(out, file, "")
                } else {
                    addFileToZip(out, file, "")
                }
            }
        }
    }

    private fun addFileToZip(out: java.util.zip.ZipOutputStream, file: File, path: String) {
        val entry = java.util.zip.ZipEntry("$path${file.name}")
        out.putNextEntry(entry)
        file.inputStream().use { input ->
            input.copyTo(out)
        }
        out.closeEntry()
    }

    private fun addFolderToZip(out: java.util.zip.ZipOutputStream, folder: File, path: String) {
        folder.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                addFolderToZip(out, child, "$path${folder.name}/")
            } else {
                addFileToZip(out, child, "$path${folder.name}/")
            }
        }
    }
}
