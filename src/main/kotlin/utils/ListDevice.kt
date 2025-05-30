import java.io.BufferedReader
import java.io.IOException

fun executeAdbCommand(command: String): List<String> {
    return try {
        val process = ProcessBuilder("adb", *command.split(" ").toTypedArray()).start()
        val reader = BufferedReader(process.inputStream.reader())
        reader.readLines()
    } catch (e: IOException) {
        emptyList()
    }
}
fun pullFileFromAndroidDevice(remotePath: String, localPath: String) {
    val process = ProcessBuilder("adb", "pull", remotePath, localPath).start()
    val reader = BufferedReader(process.inputStream.reader())
    val result = reader.readLines()
    println("拉取结果：")
    result.forEach { println(it) }
}

fun listFilesOnAndroidDevice(path: String = "/sdcard/") {
    val output = executeAdbCommand("shell ls -l $path")
    println("文件列表（路径: $path）：")
    output.forEach { line -> println(line) }
}
fun main() {
    // 列出/sdcard/目录下的文件
    listFilesOnAndroidDevice("/sdcard/")

    // 拉取文件示例（根据需要修改路径）
    pullFileFromAndroidDevice("/sdcard/example.txt", "/Users/yourname/Desktop/example.txt")
}
