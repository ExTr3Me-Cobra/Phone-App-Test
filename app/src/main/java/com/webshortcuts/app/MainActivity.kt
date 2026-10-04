package com.webshortcuts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.webshortcuts.app.ui.EditorScreen
import com.webshortcuts.app.ui.ListScreen
import com.webshortcuts.app.ui.WebShortcutsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WebShortcutsTheme {
                App()
            }
        }
    }
}

private sealed interface Screen {
    data object List : Screen

    /** [shortcut] is null when creating a new one. */
    data class Editor(val shortcut: WebShortcut?) : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val store = remember { ShortcutStore(context) }
    var shortcuts by remember { mutableStateOf(store.load()) }
    var pinnedIds by remember { mutableStateOf(PinnedShortcuts.pinnedIds(context)) }
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var blankBadgeOn by remember { mutableStateOf(BlankBadge.isOn(context)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // The user may have accepted/declined the "Add to Home screen" pop-up or removed icons.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        pinnedIds = PinnedShortcuts.pinnedIds(context)
    }

    when (val current = screen) {
        Screen.List -> ListScreen(
            shortcuts = shortcuts,
            pinnedIds = pinnedIds,
            store = store,
            snackbar = snackbar,
            onNew = { screen = Screen.Editor(null) },
            onEdit = { screen = Screen.Editor(it) },
            onDelete = { shortcut, disableIcon ->
                if (disableIcon) {
                    PinnedShortcuts.disable(context, shortcut.id, "This shortcut was deleted in Web Shortcuts.")
                }
                store.delete(shortcut.id)
                shortcuts = store.load()
                BlankBadge.sync(context, shortcuts)
            },
            blankBadgeOn = blankBadgeOn,
            onBlankBadgeChange = { on ->
                BlankBadge.setOn(context, on, shortcuts)
                blankBadgeOn = on
            },
        )

        is Screen.Editor -> EditorScreen(
            existing = current.shortcut,
            isPinned = current.shortcut != null && current.shortcut.id in pinnedIds,
            store = store,
            onBack = { screen = Screen.List },
            onFinished = { message ->
                shortcuts = store.load()
                pinnedIds = PinnedShortcuts.pinnedIds(context)
                screen = Screen.List
                scope.launch { snackbar.showSnackbar(message) }
            },
        )
    }
}
