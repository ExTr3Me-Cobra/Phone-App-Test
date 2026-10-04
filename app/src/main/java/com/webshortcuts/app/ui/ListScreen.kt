package com.webshortcuts.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.webshortcuts.app.ShortcutStore
import com.webshortcuts.app.WebShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    shortcuts: List<WebShortcut>,
    pinnedIds: Set<String>,
    store: ShortcutStore,
    snackbar: SnackbarHostState,
    onNew: () -> Unit,
    onEdit: (WebShortcut) -> Unit,
    onDelete: (WebShortcut, disableIcon: Boolean) -> Unit,
) {
    var toDelete by remember { mutableStateOf<WebShortcut?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Web Shortcuts") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New shortcut") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (shortcuts.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No shortcuts yet.\nTap \"New shortcut\" to put a website on your home screen " +
                        "with your own icon.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(shortcuts, key = { it.id }) { shortcut ->
                    ShortcutRow(
                        shortcut = shortcut,
                        pinned = shortcut.id in pinnedIds,
                        store = store,
                        onClick = { onEdit(shortcut) },
                        onDelete = { toDelete = shortcut },
                    )
                }
            }
        }
    }

    toDelete?.let { shortcut ->
        val pinned = shortcut.id in pinnedIds
        var disableIcon by remember(shortcut.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${shortcut.label}\"?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (pinned) {
                        Text(
                            "This removes it from this list. Apps can't remove icons from the " +
                                "home screen, so the icon stays there: long-press it and choose " +
                                "Remove to get rid of it.",
                        )
                        Row(
                            Modifier.clickable { disableIcon = !disableIcon },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = disableIcon, onCheckedChange = { disableIcon = it })
                            Text("Also make the home screen icon stop working (greyed out)")
                        }
                    } else {
                        Text("It isn't on your home screen, so this just removes it from the list.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(shortcut, pinned && disableIcon)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ShortcutRow(
    shortcut: WebShortcut,
    pinned: Boolean,
    store: ShortcutStore,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val icon by produceState<Bitmap?>(null, shortcut.id, shortcut.updatedAt) {
        value = withContext(Dispatchers.IO) { store.loadIcon(shortcut.id) }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                icon?.let { RenderedIconPreview(it, MaskShape.SQUIRCLE, 52.dp) }
            }
        },
        headlineContent = { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(shortcut.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.size(2.dp))
                Text(
                    if (pinned) "On home screen" else "Not on home screen — tap to add it",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete ${shortcut.label}")
            }
        },
    )
}
