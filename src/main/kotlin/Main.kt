import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import utils.ClipboardAction

fun main() = application {
    // 管理所有窗口
    val windows = remember { mutableStateListOf(Unit) }
    // 全局剪切板（所有窗口共享）
    var clipboard by remember { mutableStateOf<Pair<List<String>, ClipboardAction>?>(null) }
    val iconPainter = painterResource("icons/app_icon.png")
    val iconBitmap = remember { iconPainter }

    // 确保至少有一个窗口
    LaunchedEffect(Unit) {
        if (windows.isEmpty()) windows.add(Unit)
    }

    windows.forEachIndexed { idx, _ ->
        Window(
            onCloseRequest = { windows.removeAt(idx) },
            title = "Explore #${idx + 1}",
            icon = iconBitmap

        ) {
            FileExplorerMainFrame(
                windowIndex = idx,
                onRequestNewWindow = { windows.add(Unit) },
                clipboard = clipboard,
                setClipboard = { clipboard = it }
            )
        }
    }
}