package utils

interface AbstractFile {
    val name: String
    val path: String
    val isDirectory: Boolean

    fun listFiles(): List<AbstractFile>
    fun renameTo(newPath: String): Boolean
    fun copyTo(destPath: String): Boolean
    fun moveTo(destPath: String): Boolean
    fun delete(): Boolean
}
