import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utils.*
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane



enum class FileTypeGroup(val displayName: String, val extensions: Set<String>) {
    FOLDERS("文件夹", emptySet()),
    IMAGES("图片", setOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp")),
    DOCUMENTS("文档", setOf(".txt", ".doc", ".docx", ".pdf", ".rtf")),
    EXECUTABLES("可执行文件", setOf(".exe", ".sh", ".bat", ".msi")),
    AUDIO_FILES("音频", setOf(".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a")),
    VIDEO_FILES("视频", setOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv")),
    ARCHIVES("压缩包", setOf(".zip", ".tar.gz", ".7z", ".rar", ".tar", ".bz2")),
    SPREADSHEETS("表格", setOf(".xls", ".xlsx", ".csv", ".ods", ".numbers")),
    PRESENTATIONS("演示文稿", setOf(".ppt", ".pptx", ".key")),
    CODE_FILES("代码文件", setOf(".kt", ".java", ".py", ".js", ".ts", ".cpp", ".c", ".go", ".rs", ".php", ".rb")),
    WEB_FILES("网页文件", setOf(".html", ".htm", ".css", ".json", ".xml")),
    DATABASE_FILES("数据库文件", setOf(".sql", ".db", ".sqlite", ".mdb", ".accdb")),
    OTHERS("其他", emptySet())
}


fun groupFilesByType(files: List<FileSystemItem>): Map<String, List<FileSystemItem>> {
    val grouped = mutableMapOf<String, MutableList<FileSystemItem>>()

    files.forEach { file ->
        val key = when {
            file.isDirectory -> FileTypeGroup.FOLDERS.displayName
            else -> {
                val ext = (file.name.substringAfterLast(".", "")).lowercase()
                val typeGroup = FileTypeGroup.values().firstOrNull { it.extensions.contains(".$ext") }
                typeGroup?.displayName ?: FileTypeGroup.OTHERS.displayName
            }
        }

        grouped.getOrPut(key) { mutableListOf() }.add(file)
    }

    return grouped
}

// ==== 工具函数 ====

fun listFiles(path: String, showHidden: Boolean = false): List<FileSystemItem> {
    return when {
        path.startsWith("adb://") -> {
            try {
                val adbFile = AdbFile.fromPath(path)
                adbFile.listFiles().map { child ->
                    FileSystemItem(
                        name = child.name,
                        path = child.path,
                        isDirectory = child.isDirectory
                    )
                }.filterNot { !showHidden && it.name.startsWith(".") }
            } catch (e: Exception) {
                emptyList()
            }
        }

        else -> {
            val file = File(path)
            if (!file.exists() || !file.isDirectory) emptyList()
            else file.listFiles()?.map { LocalFile(it).toFileSystemItem() }?.filterNot {
                !showHidden && it.name.startsWith(".")
            } ?: emptyList()
        }
    }
}

fun AbstractFile.toFileSystemItem(): FileSystemItem =
    FileSystemItem(name = this.name, path = this.path, isDirectory = this.isDirectory)

fun renameFile(oldPath: String, newPath: String): Boolean {
    return if (oldPath.startsWith("adb://") || newPath.startsWith("adb://")) {
        AdbFile.fromPath(oldPath).renameTo(newPath)
    } else {
        val src = File(oldPath)
        val dest = File(newPath)
        src.exists() && !dest.exists() && src.renameTo(dest)
    }
}

fun copyFile(srcPath: String, destPath: String): Boolean {
    println("Copying from $srcPath to $destPath")

    return when {
        srcPath.startsWith("adb://") && destPath.startsWith("adb://") -> {
            // Android -> Android
            val src = AdbFile.fromPath(srcPath)
            val dest = AdbFile.fromPath(destPath)
            src.copyTo(dest.path)
        }

        srcPath.startsWith("adb://") -> {
            // Android -> Local
            val src = AdbFile.fromPath(srcPath)
            src.copyTo(destPath)
        }

        destPath.startsWith("adb://") -> {
            // Local -> Android
            val src = LocalFile(File(srcPath))
            src.copyTo(destPath)
        }

        else -> {
            // Local -> Local
            val src = File(srcPath)
            val dest = File(destPath)
            try {
                if (src.isDirectory) {
                    dest.mkdirs()
                    src.listFiles()?.forEach {
                        copyFile(it.absolutePath, "${dest.path}/${it.name}")
                    }
                    true
                } else {
                    dest.outputStream().use { output ->
                        src.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}

fun moveFile(srcPath: String, destPath: String): Boolean {
    return when {
        srcPath.startsWith("adb://") || destPath.startsWith("adb://") -> {
            val src = if (srcPath.startsWith("adb://")) AdbFile.fromPath(srcPath) else LocalFile(File(srcPath))
            src.moveTo(destPath)
        }
        else -> {
            val success = copyFile(srcPath, destPath)
            if (success) {
                val src = File(srcPath)
                if (src.isDirectory) src.deleteRecursively() else src.delete()
            }
            success
        }
    }
}

fun openFileWithPrompt(filePath: String): Boolean {
    if (filePath.startsWith("adb://")) {
        JOptionPane.showMessageDialog(null, "暂不支持直接在电脑上打开手机内文件", "无法打开", JOptionPane.ERROR_MESSAGE)
        return false
    }
    val file = File(filePath)
    if (!file.exists()) {
        JOptionPane.showMessageDialog(null, "文件不存在", "无法打开", JOptionPane.ERROR_MESSAGE)
        return false
    }
    if (!file.isFile) {
        JOptionPane.showMessageDialog(null, "该项目不是文件", "无法打开", JOptionPane.ERROR_MESSAGE)
        return false
    }
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        JOptionPane.showMessageDialog(null, "当前系统不支持打开文件", "无法打开", JOptionPane.ERROR_MESSAGE)
        return false
    }
    val result = JOptionPane.showConfirmDialog(null, "确定要打开文件: ${file.name} 吗？", "打开文件", JOptionPane.YES_NO_OPTION)
    if (result == JOptionPane.YES_OPTION) {
        try {
            Desktop.getDesktop().open(file)
            return true
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, "打开文件失败: ${e.message}", "无法打开", JOptionPane.ERROR_MESSAGE)
        }
    }
    return false
}

fun deleteFile(path: String): Boolean {
    return if (path.startsWith("adb://")) {
        AdbFile.fromPath(path).delete()
    } else {
        val file = File(path)
        if (!file.exists()) false
        else if (file.isDirectory) file.deleteRecursively() else file.delete()
    }
}

// ==== 收藏夹持久化 ====

private fun getFavoritesFilePath(): String {
    val home = System.getProperty("user.home") ?: "."
    return "$home/.fileexplorer_favorites"
}

fun loadFavoritesFromDisk(): List<String> {
    return try {
        val path = Paths.get(getFavoritesFilePath())
        if (Files.exists(path)) {
            // 读取并过滤掉不存在的路径
            Files.readAllLines(path)
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filter { File(it).exists() } // ✅ 只保留存在的路径
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}


fun saveFavoritesToDisk(favorites: List<String>) {
    try {
        val path = Paths.get(getFavoritesFilePath())
        Files.write(path, favorites, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    } catch (_: Exception) {
    }
}

// ==== UI 主体 ====

@Composable
fun FileExplorerUI(
    clipboard: Pair<List<String>, ClipboardAction>?,
    setClipboard: (Pair<List<String>, ClipboardAction>?) -> Unit,
) {
    var showAdbProgress by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(System.getProperty("user.home")!!) }
    var directoryContents by remember { mutableStateOf(listOf<FileSystemItem>()) }
    var selectedItem by remember { mutableStateOf<FileSystemItem?>(null) }
    var selectedFiles by remember { mutableStateOf<Set<FileSystemItem>>(emptySet()) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf<Offset?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileSystemItem?>(null) }
    var favorites by remember { mutableStateOf(mutableStateListOf<String>().apply { addAll(loadFavoritesFromDisk()) }) }
    var pathHistory by remember { mutableStateOf(listOf(currentPath)) }
    var isShowHide by remember { mutableStateOf(false) }

    val externalDrives by produceState(initialValue = listExternalDrivesMac()) {
        while (true) {
            value = listExternalDrivesMac()
            delay(2000)
        }
    }
    var androidFolders = AdbUtils.listDevices().map { device ->
        FileSystemItem(
            name = device.name,
            path = "adb://${device.serial}/sdcard",
            isDirectory = true
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            androidFolders = AdbUtils.listDevices().map { device ->
                FileSystemItem(
                    name = device.name,
                    path = "adb://${device.serial}/sdcard",
                    isDirectory = true
                )
            }
            delay(2000)
        }
    }

    val fixedFolders = buildList {
        add(FileSystemItem("Home", System.getProperty("user.home")!!, true))
        add(FileSystemItem("下载", "${System.getProperty("user.home")}/Downloads", true))
        add(FileSystemItem("文档", "${System.getProperty("user.home")}/Documents", true))
        addAll(androidFolders)
        addAll(externalDrives)
    }

    LaunchedEffect(currentPath, isShowHide) {
        directoryContents = listFiles(currentPath, isShowHide)
    }

    fun toggleFileSelection(file: FileSystemItem) {
        selectedFiles = if (file in selectedFiles) {
            selectedFiles - file
        } else {
            selectedFiles + file
        }
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationSidebar(
                fixedFolders = fixedFolders,
                favorites = favorites,
                onNavigate = { path ->
                    if (path != currentPath) {
                        pathHistory = pathHistory + path
                        currentPath = path
                        selectedFiles = emptySet()
                    }
                },
                onAddToFavorites = { item ->
                    val path = item.path
                    if (path !in favorites) {
                        favorites.add(path)
                        saveFavoritesToDisk(favorites)
                    }
                },
                selectedPath = currentPath
            )

            Box(modifier = Modifier.weight(4f).fillMaxHeight()) {
                Column(Modifier.fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = {
                                val parent = when {
                                    currentPath.startsWith("adb://") -> {
                                        val match = Regex("""adb://[^/]+(/.*)""").find(currentPath)
                                        val pathPart = match?.groupValues?.getOrNull(1) ?: "/"
                                        val up = pathPart.substringBeforeLast('/', "")
                                        if (up.isEmpty() || up == "/") currentPath else currentPath.substringBeforeLast('/')
                                    }
                                    else -> File(currentPath).parent
                                }
                                if (parent != null && parent != currentPath) {
                                    pathHistory = pathHistory + parent
                                    currentPath = parent
                                    selectedFiles = emptySet()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource("icons/back.png"),
                                contentDescription = "返回上一级",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        AddressBar(currentPath, onPathChange = { newPath ->
                            if (newPath != currentPath) {
                                pathHistory = pathHistory + newPath
                                currentPath = newPath
                                selectedFiles = emptySet()
                            }
                        })
                    }

                    FileListPanel(
                        files = directoryContents,
                        onOpen = { item ->
                            if (selectedFiles.isNotEmpty()) {
                                toggleFileSelection(item)
                            } else {
                                val safePath = if (item.path.startsWith("adb://")) {
                                    val cleaned = item.path.removePrefix("adb://")
                                    "adb://${cleaned.split("/").joinToString("/") { it.trim('/') }}"
                                } else {
                                    item.path
                                }

                                if (item.isDirectory) {
                                    pathHistory = pathHistory + safePath
                                    currentPath = safePath
                                    //selectedFiles = emptySet()
                                } else {
                                    openFileWithPrompt(item.path)
                                }
                            }
                        },
                        onRightClick = { item, offset ->
                            if (selectedFiles.isEmpty()) {
                                selectedItem = item
                            }
                            contextMenuPosition = offset
                        },
                        selectedItem = selectedItem,
                        onSelect = { item ->
                            if (selectedFiles.isNotEmpty()) {
                                toggleFileSelection(item)
                            } else {
                                selectedItem = item
                            }
                        },
                        selectedFiles = selectedFiles,
                        onToggleSelection = ::toggleFileSelection
                    )
                }

                if (selectedItem != null && contextMenuPosition != null || selectedFiles.isNotEmpty() && contextMenuPosition != null) {
                    val pos = contextMenuPosition!!
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(pos.x.toInt(), pos.y.toInt()),
                        onDismissRequest = {
                            contextMenuPosition = null
                        }
                    ) {
                        ContextualMenu(
                            onDismiss = { contextMenuPosition = null },
                            onRename = {
                                if (selectedFiles.size == 1) {
                                    selectedItem = selectedFiles.first()
                                    showRenameDialog = true
                                }
                            },
                            onCut = {
                                if (selectedFiles.isNotEmpty()) {
                                    setClipboard(Pair(selectedFiles.map { it.path }, ClipboardAction.CUT))
                                    selectedFiles = emptySet()
                                }
                            },
                            onCopy = {
                                if (selectedFiles.isNotEmpty()) {
                                    setClipboard(Pair(selectedFiles.map { it.path }, ClipboardAction.COPY))
                                    selectedFiles = emptySet()
                                }
                            },
                            onPaste = {
                                clipboard?.let { (srcPaths, action) ->
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            srcPaths.forEach { src ->
                                                val dest = "$currentPath/${File(src).name}"
                                                when (action) {
                                                    ClipboardAction.COPY -> copyFile(src, dest)
                                                    ClipboardAction.CUT -> moveFile(src, dest)
                                                }
                                            }
                                        }
                                        directoryContents = listFiles(currentPath)
                                    }
                                }
                            },
                            onAddToFavorites = {
                                if (selectedFiles.size == 1) {
                                    val path = selectedFiles.first().path
                                    favorites.add(path)
                                }
                            },
                            onDelete = {
                                deleteTarget = if (selectedFiles.size == 1) selectedFiles.first() else null
                                showDeleteDialog = true
                                contextMenuPosition = null
                                selectedItem = null
                            }
                        )

                    }
                }

                if (showRenameDialog && (selectedItem != null || selectedFiles.size == 1)) {
                    val renameTarget = selectedItem ?: selectedFiles.firstOrNull()
                    if (renameTarget != null) {
                        RenameDialog(
                            initialName = renameTarget.name,
                            onDismiss = {
                                showRenameDialog = false
                                selectedItem = null
                            },
                            onConfirm = { name ->
                                val oldPath = renameTarget.path
                                val newPath = if (oldPath.startsWith("adb://")) {
                                    oldPath.substringBeforeLast('/') + "/$name"
                                } else {
                                    "${File(oldPath).parent}/$name"
                                }
                                showRenameDialog = false
                                selectedItem = null
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        renameFile(oldPath, newPath)
                                    }
                                    directoryContents = listFiles(currentPath)
                                }
                            },
                            onNameChange = {}
                        )
                    }
                }

                if (showDeleteDialog) {
                    ConfirmDeleteDialog(
                        filename = if (selectedFiles.size > 1) "${selectedFiles.size}个文件"
                        else deleteTarget?.name ?: selectedFiles.firstOrNull()?.name ?: "",
                        onConfirm = {
                            val pathsToDelete = if (selectedFiles.isNotEmpty()) {
                                selectedFiles.map { it.path }
                            } else {
                                listOfNotNull(deleteTarget?.path)
                            }
                            showDeleteDialog = false
                            deleteTarget = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    pathsToDelete.forEach { path ->
                                        if (path.isNotEmpty()) {
                                            deleteFile(path)
                                        }
                                    }
                                }
                                selectedFiles = emptySet()
                                directoryContents = listFiles(currentPath)
                            }
                        },
                        onDismiss = {
                            showDeleteDialog = false
                            deleteTarget = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmDeleteDialog(
    filename: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("确定要删除文件/文件夹：$filename 吗？")
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                    ) {
                        Text("删除", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onNameChange: (String) -> Unit
) {
    var state by remember { mutableStateOf(initialName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("请输入新名称")
                TextField(
                    value = state,
                    onValueChange = {
                        state = it
                        onNameChange(it)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = { onConfirm(state) }) {
                        Text("确认")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@Composable
fun AddressBar(path: String, onPathChange: (String) -> Unit) {
    TextField(
        value = path,
        onValueChange = onPathChange,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        label = { Text("地址") }
    )
}
data class FileInfo(
    val type: String = "",
    val size: String = "",
    val modifiedTime: String = ""
)
fun formatFileSize(size: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var index = 0
    var fileSize = size.toDouble()

    while (fileSize >= 1024 && index < units.size - 1) {
        fileSize /= 1024
        index++
    }

    return String.format("%.1f %s", fileSize, units[index])
}

fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
    return sdf.format(date)
}

@Composable
fun FileListPanel(
    files: List<FileSystemItem>,
    onOpen: (FileSystemItem) -> Unit,
    onRightClick: (FileSystemItem, Offset) -> Unit,
    selectedItem: FileSystemItem?,
    onSelect: (FileSystemItem) -> Unit,
    selectedFiles: Set<FileSystemItem>,
    onToggleSelection: (FileSystemItem) -> Unit
) {
    val groupedFiles by remember(files) {
        derivedStateOf {
            groupFilesByType(files.sortedBy { !it.isDirectory })
        }
    }

    LazyColumn(modifier = Modifier.padding(8.dp).fillMaxHeight()) {
        groupedFiles.forEach { (groupName, groupItems) ->

            item {
                Text(
                    text = groupName,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Divider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选择", fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                    Text("名称", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
                    Text("类型", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f))
                    Text("大小", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f))
                    Text("修改时间", fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f))
                }
                Divider()
            }

            items(groupItems) { file ->
                val isSelected = file == selectedItem
                val isChecked = file in selectedFiles
                var lastClickTime by remember(file) { mutableStateOf(0L) }
// ✅ 兼容写法
                var selectedFiles by remember { mutableStateOf(mutableSetOf<FileSystemItem>()) }

                fun toggleFileSelection(file: FileSystemItem) {
                    val newSet = selectedFiles.toMutableSet()
                    if (file in newSet) {
                        newSet.remove(file)
                    } else {
                        newSet.add(file)
                    }
                    selectedFiles = newSet
                }


                val fileInfo = remember(file) {
                    when {
                        file.path.startsWith("adb://") -> {
                            FileInfo(
                                type = if (file.isDirectory) "设备文件夹" else "设备文件",
                                size = "-",
                                modifiedTime = "-"
                            )
                        }
                        else -> {
                            val localFile = File(file.path)
                            FileInfo(
                                type = if (localFile.isDirectory) "文件夹" else "文件",
                                size = formatFileSize(localFile.length()),
                                modifiedTime = formatDate(localFile.lastModified())
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(8.dp)
                        .pointerInput(file) {
                            awaitEachGesture {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pos = event.changes.firstOrNull()?.position ?: Offset.Zero
                                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                        //如果选择的是空则选择
                                        val now = System.currentTimeMillis()
                                        if (now - lastClickTime < 100) continue
                                        lastClickTime = now

                                        //输出点击的信息,checkbox,是否在选择内
                                        println("点击了文件: ${file.name}, 选择状态: ${isChecked}, 是否在选中内: ${selectedFiles.contains(file)}")

                                        //获取左侧checkbox状态
                                        if (!selectedFiles.contains(file)) {
                                            onToggleSelection(file)
                                        }

                                        onRightClick(file, pos)
                                    }
                                    if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                        val now = System.currentTimeMillis()
                                        if (now - lastClickTime < 100) continue
                                        lastClickTime = now
                                        if (pos.x < 100) return@awaitEachGesture
                                        //onSelect(file)
                                        if (!file.isDirectory) {
                                            openFileWithPrompt(file.path)
                                        } else {
                                            onOpen(file)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = {
                            toggleFileSelection(file)
                            onToggleSelection(file)
                        },
                        modifier = Modifier.width(48.dp)
                    )

                    Row(
                        modifier = Modifier
                            .weight(0.4f)
                            .clickable { onToggleSelection(file) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CustomIcon(isDirectory = file.isDirectory)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = file.name)
                    }

                    Text(text = fileInfo.type, modifier = Modifier.weight(0.2f))
                    Text(text = fileInfo.size, modifier = Modifier.weight(0.2f))
                    Text(text = fileInfo.modifiedTime, modifier = Modifier.weight(0.2f))
                }
            }
        }
    }
}



@Composable
fun CustomIcon(isDirectory: Boolean) {
    val resourceId = if (isDirectory) "icons/folder.png" else "icons/file.png"
    Image(
        painter = painterResource(resourceId),
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun NavigationSidebar(
    fixedFolders: List<FileSystemItem>,
    favorites: List<String>,
    onNavigate: (String) -> Unit,
    onAddToFavorites: (FileSystemItem) -> Unit,
    selectedPath: String
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .fillMaxHeight()
            .padding(8.dp)
    ) {
        Text("常用目录", style = MaterialTheme.typography.subtitle1)
        fixedFolders.forEach { item ->
            val isSelected = item.path == selectedPath
            Text(
                text = item.name,
                color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(vertical = 4.dp)
                    .clickable { onNavigate(item.path) }
            )
        }
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text("收藏夹", style = MaterialTheme.typography.subtitle1)
        if (favorites.isEmpty()) {
            Text("暂无收藏", style = MaterialTheme.typography.caption)
        } else {
            favorites.forEach { path ->
                val file = File(path)
                val isSelected = path == selectedPath
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .padding(vertical = 4.dp)
                        .clickable {
                            if (file.exists()) {
                                if (file.isDirectory) {
                                    onNavigate(path)
                                } else {
                                    openFileWithPrompt(path)
                                }
                            }else { }
                        }
                ) {
                    CustomIcon(isDirectory = file.isDirectory)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
@Composable
fun AdbProgressDialog(
    visible: Boolean,
    content: @Composable () -> Unit = { Text("正在执行 ADB 操作...") }
) {
    if (visible) {
        Dialog(onDismissRequest = {}) {
            Surface {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun ContextualMenu(
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onAddToFavorites: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        elevation = 8.dp,
        color = MaterialTheme.colors.surface,
        modifier = Modifier
            .width(IntrinsicSize.Min)
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
            MenuItem("重命名", onClick = { onRename(); onDismiss() })
            MenuItem("剪切", onClick = { onCut(); onDismiss() })
            MenuItem("复制", onClick = { onCopy(); onDismiss() })
            MenuItem("粘贴", onClick = { onPaste(); onDismiss() })
            MenuItem("添加收藏", onClick = { onAddToFavorites(); onDismiss() })
            Divider()
            MenuItem("删除", onClick = { onDelete(); onDismiss() })
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clickable(onClick = onClick)
    )
}