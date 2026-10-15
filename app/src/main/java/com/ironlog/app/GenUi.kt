package com.ironlog.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Progress of the picture maker, with a Stop button. Shows nothing when idle with no news. */
@Composable
fun GenPanel() {
    val s by Gen.state.collectAsState()
    val context = LocalContext.current
    if (!s.running && s.message == null) return
    Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        if (s.running) {
            Text("Making AI pictures: ${s.done} of ${s.total}", fontWeight = FontWeight.Bold)
            if (s.current.isNotBlank()) Text("Now: ${s.current}", color = Sub)
            LinearProgressIndicator(
                progress = { s.done / s.total.coerceAtLeast(1).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Text("Keeps going with the app closed – see the notification.", color = Sub, fontSize = 12.sp)
            GreyButton("Stop", textColor = Red) { Gen.stop(context) }
        }
        s.message?.let { Text(it, color = if (s.running) Sub else Blue) }
    }
}

/** Asks: only exercises without a picture, or replace them all (with rough cost), then starts. */
@Composable
fun GenDialog(title: String, ids: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = Store.profile.value
    val missing = ids.filter { !Store.hasImage(it) }
    val each = Pictures.price(p.imageQuality)
    fun cost(n: Int) = "about $" + "%.2f".format(n * each)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (p.apiKey.isBlank()) {
                    Text("First add your OpenAI API key in Settings.", color = Red)
                } else {
                    Text(
                        "${ids.size} exercises, ${ids.size - missing.size} already have a picture. " +
                            "Quality: ${p.imageQuality} (${cost(1)} each at OpenAI's listed price – check your account).",
                    )
                    Text(
                        "Pictures are made in the background, so you can close the app. Big batches take hours.",
                        color = Sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            Column {
                if (p.apiKey.isNotBlank()) {
                    TextButton(enabled = missing.isNotEmpty(), onClick = { Gen.start(context, missing, false, title); onDismiss() }) {
                        Text("Only ones without a picture (${missing.size}) · ${cost(missing.size)}")
                    }
                    TextButton(onClick = { Gen.start(context, ids, true, title); onDismiss() }) {
                        Text("New pictures for all ${ids.size} (replaces) · ${cost(ids.size)}", color = Red)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
