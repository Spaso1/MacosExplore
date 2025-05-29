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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Popup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane
import utils.ClipboardAction
import utils.rememberAndroidFolders
import utils.rememberUsbFolders

// ==== 工具函数（utils）====

fun listFiles(path: String): List<FileSystemItem> {
    val file = File(path)
    if (!file.exists() || !file.isDirectory) return emptyList()
    return file.listFiles()?.map {
        FileSystemItem(it.name, it.absolutePath, it.isDirectory)
    }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
}

fun renameFile(oldPath: String, newPath: String): Boolean {
    val src = File(oldPath)
    val dest = File(newPath)
    return src.exists() && !dest.exists() && src.renameTo(dest)
}

fun copyFile(srcPath: String, destPath: String): Boolean {
    val src = File(srcPath)
    val dest = File(destPath)
    return try {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach {
                copyFile(it.absolutePath, dest.absolutePath + File.separator + it.name)
            }
        } else {
            dest.outputStream().use { output ->
                src.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun moveFile(srcPath: String, destPath: String): Boolean {
    val success = copyFile(srcPath, destPath)
    if (success) {
        val src = File(srcPath)
        if (src.isDirectory) src.deleteRecursively() else src.delete()
    }
    return success
}

fun openFileWithPrompt(filePath: String): Boolean {
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
    val file = File(path)
    if (!file.exists()) return false
    return if (file.isDirectory) file.deleteRecursively() else file.delete()
}

// ==== 收藏夹持久化 ====

private fun getFavoritesFilePath(): String {
    val home = System.getProperty("user.home") ?: "."
    return "$home/.fileexplorer_favorites"
}

// 读取收藏
fun loadFavoritesFromDisk(): List<String> {
    return try {
        val path = Paths.get(getFavoritesFilePath())
        if (Files.exists(path)) {
            Files.readAllLines(path).map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

// 保存收藏
fun saveFavoritesToDisk(favorites: List<String>) {
    try {
        val path = Paths.get(getFavoritesFilePath())
        Files.write(path, favorites, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    } catch (_: Exception) {
    }
}

@Composable
fun FileExplorerUI(
    clipboard: Pair<String, ClipboardAction>?,
    setClipboard: (Pair<String, ClipboardAction>?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(System.getProperty("user.home")!!) }
    var directoryContents by remember { mutableStateOf(listOf<FileSystemItem>()) }
    var selectedItem by remember { mutableStateOf<FileSystemItem?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var contextMenuPosition by remember { mutableStateOf<Offset?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileSystemItem?>(null) }
    var favorites by remember { mutableStateOf(mutableStateListOf<String>().apply { addAll(loadFavoritesFromDisk()) }) }
    var pathHistory by remember { mutableStateOf(listOf(currentPath)) }

    val androidFolders = rememberAndroidFolders()
    val usbFolders = rememberUsbFolders()

    val fixedFolders = buildList {
        add(FileSystemItem("Home", System.getProperty("user.home")!!, true))
        add(FileSystemItem("下载", "${System.getProperty("user.home")}/Downloads", true))
        add(FileSystemItem("文档", "${System.getProperty("user.home")}/Documents", true))
        androidFolders.forEach { add(FileSystemItem("Android:${it.name}", it.path, true)) }
        usbFolders.forEach { add(FileSystemItem("U盘:${it.name}", it.path, true)) }
    }

    LaunchedEffect(currentPath) {
        directoryContents = listFiles(currentPath)
    }

    MaterialTheme {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧：固定目录 + 收藏夹
            NavigationSidebar(
                fixedFolders = fixedFolders,
                favorites = favorites,
                onNavigate = { path ->
                    if (path != currentPath) {
                        pathHistory = pathHistory + path
                        currentPath = path
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

            // 右侧：当前目录内容
            Box(modifier = Modifier.weight(4f).fillMaxHeight()) {
                Column(Modifier.fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        // 返回上一级按钮
                        IconButton(
                            onClick = {
                                val parent = File(currentPath).parent
                                if (parent != null && parent != currentPath) {
                                    pathHistory = pathHistory + parent
                                    currentPath = parent
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
                            }
                        })
                    }

                    FileListPanel(
                        files = directoryContents,
                        onOpen = { item ->
                            if (item.isDirectory) {
                                pathHistory = pathHistory + item.path
                                currentPath = item.path
                            } else {
                                openFileWithPrompt(item.path)
                            }
                        },
                        onRightClick = { item, offset ->
                            selectedItem = item
                            contextMenuPosition = offset
                        },
                        selectedItem = selectedItem,
                        onSelect = { item -> selectedItem = item }
                    )
                }

                // 右键菜单
                if (selectedItem != null && contextMenuPosition != null) {
                    val pos = contextMenuPosition!!
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(pos.x.toInt(), pos.y.toInt()),
                        onDismissRequest = {
                            contextMenuPosition = null
                        }
                    ) {
                        ContextualMenu(
                            onDismiss = {
                                contextMenuPosition = null
                            },
                            onRename = {
                                showRenameDialog = true
                            },
                            onCut = {
                                setClipboard(Pair(selectedItem!!.path, ClipboardAction.CUT))
                            },
                            onCopy = {
                                setClipboard(Pair(selectedItem!!.path, ClipboardAction.COPY))
                            },
                            onPaste = {
                                clipboard?.let { (src, action) ->
                                    val dest = "$currentPath/${File(src).name}"
                                    when (action) {
                                        ClipboardAction.COPY -> copyFile(src, dest)
                                        ClipboardAction.CUT -> moveFile(src, dest)
                                    }
                                    setClipboard(null)
                                    directoryContents = listFiles(currentPath)
                                }
                            },
                            onAddToFavorites = {
                                val path = selectedItem!!.path
                                if (path !in favorites) {
                                    favorites.add(path)
                                    saveFavoritesToDisk(favorites)
                                }
                            },
                            onDelete = {
                                deleteTarget = selectedItem
                                showDeleteDialog = true
                                contextMenuPosition = null
                                selectedItem = null
                            }
                        )
                    }
                }

                // 重命名对话框
                if (showRenameDialog && selectedItem != null) {
                    RenameDialog(
                        initialName = selectedItem!!.name,
                        onDismiss = {
                            showRenameDialog = false
                            selectedItem = null
                        },
                        onConfirm = { name ->
                            val oldPath = selectedItem!!.path
                            val newPath = "${File(oldPath).parent}/$name"
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
                // 删除确认对话框（异步删除）
                if (showDeleteDialog && deleteTarget != null) {
                    ConfirmDeleteDialog(
                        filename = deleteTarget!!.name,
                        onConfirm = {
                            val pathToDelete = deleteTarget!!.path
                            showDeleteDialog = false
                            deleteTarget = null
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    deleteFile(pathToDelete)
                                }
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

@Composable
fun FileListPanel(
    files: List<FileSystemItem>,
    onOpen: (FileSystemItem) -> Unit,
    onRightClick: (FileSystemItem, Offset) -> Unit,
    selectedItem: FileSystemItem?,
    onSelect: (FileSystemItem) -> Unit
) {
    LazyColumn(modifier = Modifier.padding(8.dp).fillMaxHeight()) {
        items(files) { file ->
            val isSelected = file == selectedItem
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSelected) MaterialTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .padding(4.dp)
                    .pointerInput(file) {
                        detectTapGestures(
                            onTap = { onSelect(file) },
                            onDoubleTap = { offset -> onOpen(file) },
                            onLongPress = { offset -> onRightClick(file, offset) },
                        )
                    }
                    .pointerInput(file) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pos = event.changes.firstOrNull()?.position ?: Offset.Zero

                                // 鼠标右键/触摸板双指
                                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                    onRightClick(file, pos)
                                }
                                // 鼠标左键单击选中
                                if (event.type == PointerEventType.Press && event.buttons.isPrimaryPressed) {
                                    onSelect(file)
                                }
                                // 鼠标左键双击打开
                                if (event.type == PointerEventType.Release && event.buttons.isPrimaryPressed) {
                                    // 你可以添加一个双击检测器；这里只是演示
                                    // 实际项目建议用系统的双击事件或引入计时判断
                                    onOpen(file)
                                }
                            }
                        }
                    }
            ) {
                CustomIcon(isDirectory = file.isDirectory)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = file.name)
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
                            }
                        }
                ) {
                    // 图标
                    CustomIcon(isDirectory = file.isDirectory)
                    Spacer(Modifier.width(4.dp))
                    // 单行+省略号
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