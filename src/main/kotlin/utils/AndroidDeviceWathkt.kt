package utils

import FileSystemItem
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.*
import kotlin.io.path.isDirectory
import androidx.compose.runtime.*

/**
 * 用于监听 Android 设备通过 MTP/U 盘模式连接到电脑时，自动加载 Android 文件夹。
 * 适用于 Windows（如“此电脑\设备名\内部存储设备\Download”等），
 * 也兼容 macOS（通常会挂载为 /Volumes/xxx）。
 */
fun findAndroidDeviceFolders(): List<FileSystemItem> {
    val roots = File.listRoots()
    val androidFolders = mutableListOf<FileSystemItem>()

    // Windows: 遍历“此电脑”下的盘符和挂载点
    roots.forEach { root ->
        // 常见 U 盘/手机挂载点
        val possiblePaths = listOf(
            "Android", "内部存储设备", "Phone", "SD卡", "Internal shared storage"
        )
        root.listFiles()?.forEach { sub ->
            if (possiblePaths.any { key -> sub.name.contains(key, ignoreCase = true) }) {
                androidFolders.add(FileSystemItem(sub.name, sub.absolutePath, true))
            }
        }
    }

    // macOS: /Volumes 目录下挂载的设备
    val volumes = File("/Volumes")
    if (volumes.exists()) {
        volumes.listFiles()?.forEach { vol ->
            // 略去系统盘
            if (vol.name != "Macintosh HD" && vol.isDirectory) {
                // 判断是否为 Android 设备（可自定义）
                if (File(vol, "Android").exists() || File(vol, "DCIM").exists()) {
                    androidFolders.add(FileSystemItem(vol.name, vol.absolutePath, true))
                }
            }
        }
    }

    return androidFolders
}

/**
 * 在 FileExplorerUI 中调用，自动加载 Android 文件夹（需在 Composable 内）。
 * @return List<FileSystemItem> Android设备挂载点
 */
@Composable
fun rememberAndroidFolders(): List<FileSystemItem> {
    var androidFolders by remember { mutableStateOf(emptyList<FileSystemItem>()) }

    LaunchedEffect(Unit) {
        // 启动时自动查找一次
        androidFolders = findAndroidDeviceFolders()

        // 可选：定时轮询刷新（如每10秒）
        while (true) {
            delay(10000)
            val found = findAndroidDeviceFolders()
            if (found != androidFolders) {
                androidFolders = found
            }
        }
    }
    return androidFolders
}