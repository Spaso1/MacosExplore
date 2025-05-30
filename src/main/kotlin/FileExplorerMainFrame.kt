import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import utils.ClipboardAction

@Composable
fun FileExplorerMainFrame(
    windowIndex: Int,
    onRequestNewWindow: () -> Unit,
    clipboard: Pair<List<String>, ClipboardAction>?,
    setClipboard: (Pair<List<String>, ClipboardAction>?) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    Column {
        // 顶部状态栏/标题栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(MaterialTheme.colors.primary)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            menuOffset = offset
                            showMenu = true
                        }
                    )
                }
        ) {
            Text(
                "Explore #${windowIndex + 1}",
                color = MaterialTheme.colors.onPrimary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
            )
        }

        // 右键弹出菜单
        if (showMenu) {
            Popup(
                offset = IntOffset(menuOffset.x.toInt(), menuOffset.y.toInt()),
                onDismissRequest = { showMenu = false }
            ) {
                Surface(elevation = 4.dp) {
                    Column {
                        TextButton(
                            onClick = {
                                showMenu = false
                                onRequestNewWindow()
                            }
                        ) {
                            Text("新建窗口")
                        }
                    }
                }
            }
        }

        FileExplorerUI(
            clipboard = clipboard,
            setClipboard = setClipboard,
        )
    }
}