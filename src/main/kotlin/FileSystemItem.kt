import java.io.File

data class FileSystemItem(
    val name: String,
    var path: String,
    val isDirectory: Boolean
)

// macOS下所有/Volumes目录下的挂载设备
fun listExternalDrivesMac(): List<FileSystemItem> {
    val volumesDir = File("/Volumes")
    if (!volumesDir.exists() || !volumesDir.isDirectory) return emptyList()
    return volumesDir.listFiles()?.map {
        FileSystemItem(it.name, it.absolutePath, true)
    } ?: emptyList()
}