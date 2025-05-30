package utils

import java.io.File

class LocalFile(private val file: File) : AbstractFile {
    override val name = file.name
    override val path = file.absolutePath
    override val isDirectory = file.isDirectory

    override fun listFiles(): List<AbstractFile> {
        return file.listFiles()?.map { LocalFile(it) } ?: emptyList()
    }

    override fun renameTo(newPath: String): Boolean {
        val dest = File(newPath)
        return if (!dest.exists()) file.renameTo(dest) else false
    }

    override fun copyTo(destPath: String): Boolean {
        val src = file
        val dest = File(destPath)
        dest.path
        println(src)
        println(dest)
        if (destPath.startsWith("adb://")) {
            // 从 Mac 到 Android
            val dest = AdbFile.fromPath(destPath)
            return AdbUtils.pushFileToDevice(file.absolutePath, dest.serial, dest.devicePath)
        }

        return try {
            if (src.isDirectory) {
                dest.mkdirs()
                src.listFiles()?.forEach { child ->
                    child.copyTo(File(dest, child.name))
                }
            } else {
                src.inputStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun moveTo(destPath: String): Boolean {
        val success = copyTo(destPath)
        if (success) {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
        return success
    }

    override fun delete(): Boolean {
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }
}

class AdbFile(
    val serial: String,
    val devicePath: String,
    val isDirFlag: Boolean? = null
) : AbstractFile {
    private val adbPathRegex = Regex("""adb://([^/]+)(/.*)""")

    override val name: String
        get() = devicePath.split("/").last()

    override val path: String
        get() = "adb://$serial$devicePath"

    override val isDirectory: Boolean by lazy {
        isDirFlag ?: AdbUtils.isDirectory(serial, devicePath)
    }


    override fun listFiles(): List<AbstractFile> {
        return AdbUtils.listFilesOnDevice(serial, devicePath).map {
            AdbFile(serial, it.path,  it.isDirectory)
        }
    }

    override fun renameTo(newPath: String): Boolean {
        val newAdbFile = AdbFile.fromPath(newPath)
        return AdbUtils.renameFile(serial, devicePath, newAdbFile.devicePath)
    }

    override fun copyTo(destPath: String): Boolean {
        return when {
            destPath.startsWith("adb://") -> {
                val dest = AdbFile.fromPath(destPath)
                //println(dest)
                AdbUtils.copyFile(serial, devicePath, dest.serial, dest.devicePath)
            }

            else -> {
                val destFile = File(destPath)

                if (isDirectory) {
                    // 创建目标目录
                    destFile.mkdirs()

                    // 遍历设备上的子项
                    listFiles().all { child ->
                        val childDestPath = "${destPath}/${child.name}"
                        child.copyTo(childDestPath)
                    }
                } else {
                    // 单个文件直接 pull
                    AdbUtils.pullFileFromDevice(serial, devicePath, destPath)
                }
            }
        }
    }

    override fun moveTo(destPath: String): Boolean {
        val result = copyTo(destPath)
        if (result) delete()
        return result
    }

    override fun delete(): Boolean {
        return AdbUtils.deleteFile(serial, devicePath)
    }

    companion object {
        fun fromPath(path: String): AdbFile {
            var pathUse = ""
            val temp = path
            //如果有两个adb://
            if (path.contains("adb://")) {
                pathUse = path.replaceFirst("adb://", "")
                if (pathUse.contains("adb://")) {
                    //从adb://xxadb开始截取
                    val serial = temp.split("adb://")[2]
                    pathUse = "adb://" + serial
                }else{
                    pathUse = temp
                }
            }
            println(pathUse)
            val match = Regex("""adb://([^/]+)(/.*)""").find(pathUse)
            require(match != null && match.groupValues.size >= 3) { "Invalid ADB path: $pathUse" }
            val serial = match.groupValues[1]
            val devicePath = match.groupValues[2]

            return AdbFile(serial, devicePath)
        }
    }

}
