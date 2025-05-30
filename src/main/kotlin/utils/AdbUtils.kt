package utils

import FileSystemItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class AndroidDevice(
    val serial: String,
    val name: String
)
fun getAdbPath(): String? {
    val knownPaths = listOf(
        "/usr/bin/adb",                         // macOS 标准路径
        "/opt/homebrew/bin/adb",                // Homebrew 安装路径 (M1)
        "/opt/local/bin/adb",                   // MacPorts 安装路径
        "C:\\platform-tools\\adb.exe",          // Windows
        "/usr/local/android-sdk/platform-tools/adb" // Linux SDK
    ).firstOrNull { File(it).exists() }


    if (knownPaths != null) return knownPaths

    // 方法2：从 PATH 中找
    val pathEnv = System.getenv("PATH") ?: return null
    val paths = pathEnv.split(File.pathSeparator)

    for (dir in paths) {
        val adbFile = File(dir, if (System.getProperty("os.name").contains("Windows")) "adb.exe" else "adb")
        if (adbFile.exists()) {
            return adbFile.absolutePath
        }
    }

    return null
}

object AdbUtils {

    // ==== 异步版本 ====

    suspend fun isDirectoryAsync(serial: String, path: String): Boolean {
        return withContext(Dispatchers.IO) {
            val adbPath = getAdbPath()

            val output = executeAdbCommand("-s", serial, "shell", "[ -d \"$path\" ] && echo 'dir' || echo 'not'")
            output.firstOrNull() == "dir"
        }
    }

    suspend fun listFilesOnDeviceAsync(serial: String, path: String): List<AbstractFile> {
        return withContext(Dispatchers.IO) {
            val names = executeAdbCommand(
                 "-s", serial, "shell", "ls", "-1a", path
            )
            names.filter { it != "." && it != ".." && it.isNotBlank() }.map { name ->
                val fullPath = if (path.endsWith("/")) path + name else "$path/$name"
                AdbFile(serial, fullPath, isDirectory(serial, fullPath))
            }
        }
    }

    suspend fun pullFileFromDeviceAsync(serial: String, remotePath: String, localPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("-s", serial, "pull", remotePath, localPath).start()
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))
            val errors = errorStream.readLines().joinToString("\n")

            try {
                process.waitFor()
                if (process.exitValue() != 0) {
                    println("Error pulling file: $errors")
                    false
                } else {
                    true
                }
            } finally {
                errorStream.close()
            }
        }
    }

    suspend fun pushFileToDeviceAsync(localPath: String, serial: String, remotePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder( "-s", serial, "push", localPath, remotePath).start()
            process.waitFor()
            process.exitValue() == 0
        }
    }

    suspend fun deleteFileAsync(serial: String, path: String): Boolean {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder( "-s", serial, "shell", "rm", "-rf", path).start()
            process.waitFor()
            process.exitValue() == 0
        }
    }

    suspend fun renameFileAsync(serial: String, oldPath: String, newPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val process = ProcessBuilder("-s", serial, "shell", "mv", oldPath, newPath).start()
            process.waitFor()
            process.exitValue() == 0
        }
    }

    suspend fun copyFileAsync(srcSerial: String, srcPath: String, dstSerial: String, dstPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val tmp = System.getProperty("java.io.tmpdir") + "/adbtmp/${System.currentTimeMillis()}"
            if (!pullFileFromDeviceAsync(srcSerial, srcPath, tmp)) return@withContext false
            pushFileToDeviceAsync(tmp, dstSerial, dstPath)
        }
    }
    private suspend fun executeAdbCommand(vararg args: String): List<String> {
        return withContext(Dispatchers.IO) {
            val adbPath = getAdbPath() ?: return@withContext emptyList()
            try {
                val fullArgs = listOf(adbPath) + args
                val process = ProcessBuilder(*fullArgs.toTypedArray()).start()
                val reader = BufferedReader(process.inputStream.reader())
                reader.readLines()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    // ==== 同步封装（保持原接口）====

    fun isDirectory(serial: String, path: String): Boolean =
        runBlocking { isDirectoryAsync(serial, path) }

    fun listFilesOnDevice(serial: String, path: String): List<AbstractFile> =
        runBlocking { listFilesOnDeviceAsync(serial, path) }

    fun pullFileFromDevice(serial: String, remotePath: String, localPath: String): Boolean =
        runBlocking { pullFileFromDeviceAsync(serial, remotePath, localPath) }

    fun pushFileToDevice(localPath: String, serial: String, remotePath: String): Boolean =
        runBlocking { pushFileToDeviceAsync(localPath, serial, remotePath) }

    fun deleteFile(serial: String, path: String): Boolean =
        runBlocking { deleteFileAsync(serial, path) }

    fun renameFile(serial: String, oldPath: String, newPath: String): Boolean =
        runBlocking { renameFileAsync(serial, oldPath, newPath) }

    fun copyFile(srcSerial: String, srcPath: String, dstSerial: String, dstPath: String): Boolean =
        runBlocking { copyFileAsync(srcSerial, srcPath, dstSerial, dstPath) }

    fun listDevices(): List<AndroidDevice> {
        val result = mutableListOf<AndroidDevice>()
        try {
            val process = ProcessBuilder("adb", "devices").start()
            val lines = process.inputStream.bufferedReader().readLines()
            for (line in lines) {
                if (line.endsWith("device") && !line.startsWith("List")) {
                    val serial = line.split("\t").first()
                    val name = getDeviceName(serial) ?: serial
                    result.add(AndroidDevice(serial, name))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun getDeviceName(serial: String): String? {
        return try {
            val process = ProcessBuilder("adb", "-s", serial, "shell", "getprop", "ro.product.model").start()
            process.inputStream.bufferedReader().readLine()?.trim()
        } catch (_: Exception) {
            null
        }
    }
    //检查adb状态
    fun checkAdbStatus(): Boolean {
        return try {
            val process = ProcessBuilder("adb", "devices").start()
            process.waitFor()
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
