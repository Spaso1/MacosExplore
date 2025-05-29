import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import utils.ClipboardAction

fun main() = application {
    // 管理所有窗口
    val windows = remember { mutableStateListOf(Unit) }
    // 全局剪切板（所有窗口共享）
    var clipboard by remember { mutableStateOf<Pair<String, ClipboardAction>?>(null) }

    // 确保至少有一个窗口
    LaunchedEffect(Unit) {
        if (windows.isEmpty()) windows.add(Unit)
    }

    windows.forEachIndexed { idx, _ ->
        Window(
            onCloseRequest = { windows.removeAt(idx) },
            title = "Explore #${idx + 1}"
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