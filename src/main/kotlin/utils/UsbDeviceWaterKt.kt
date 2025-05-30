package utils

import FileSystemItem
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import java.io.File

fun findUsbDeviceFolders(): List<FileSystemItem> {
    val results = mutableListOf<FileSystemItem>()

    // Windows: 检查新的盘符
    File.listRoots().forEach { root ->
        // 可选：添加卷标/属性判断更精确识别U盘
        // 这里只要是可读可写且非系统盘
        if (root.canRead() && root.canWrite()) {
            // 不显示系统盘/隐藏盘等，可以加白名单/黑名单过滤
            if (root.totalSpace > 0L && root.usableSpace > 0L) {
                results.add(FileSystemItem(root.path, root.path, true))
            }
        }
    }

    // macOS: 检查 /Volumes 下的挂载点
    val volumes = File("/Volumes")
    if (volumes.exists() && volumes.isDirectory) {
        volumes.listFiles()?.forEach { vol ->
            if (vol.name != "Macintosh HD" && vol.isDirectory) {
                results.add(FileSystemItem(vol.name, vol.absolutePath, true))
            }
        }
    }

    // Linux: 检查 /media/用户名 下的挂载点
    val user = System.getProperty("user.name") ?: ""
    val media = File("/media/$user")
    if (media.exists() && media.isDirectory) {
        media.listFiles()?.forEach { mnt ->
            if (mnt.isDirectory) {
                results.add(FileSystemItem(mnt.name, mnt.absolutePath, true))
            }
        }
    }

    return results
}

@Composable
fun rememberUsbFolders(): List<FileSystemItem> {
    var usbFolders by remember { mutableStateOf(emptyList<FileSystemItem>()) }
    LaunchedEffect(Unit) {
        usbFolders = findUsbDeviceFolders()
        while (true) {
            delay(10_000)
            val found = findUsbDeviceFolders()
            if (found != usbFolders) usbFolders = found
        }
    }
    return usbFolders
}