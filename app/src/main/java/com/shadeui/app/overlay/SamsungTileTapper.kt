package com.shadeui.app.overlay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.shadeui.app.tiles.TileDef
import kotlinx.coroutines.delay

/**
 * The workaround for Wi-Fi, Bluetooth, mobile data and the other tiles Android won't let apps
 * switch: open Samsung's own quick panel underneath Shade's (so it stays hidden), press the
 * matching tile through accessibility, read its new state, and close Samsung's panel again.
 */
object SamsungTileTapper {
    @Volatile
    var busy = false
        private set

    /** Returns the tile's new on/off state when it could be read, or null. */
    suspend fun tap(service: AccessibilityService, tile: TileDef): Result {
        busy = true
        try {
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) {
                return Result.Failed
            }
            var node: AccessibilityNodeInfo? = null
            for (attempt in 0 until 24) {
                delay(90)
                node = find(service, tile.samsungLabels)
                if (node != null) break
                // The tile may be on another page of Samsung's panel.
                if (attempt == 10 || attempt == 16) scroll(service)
            }
            if (node == null) {
                // Record what Samsung's panel did show, so the tile names can be matched.
                Diagnostics.add("no '${tile.label}' tile; saw: " + visibleLabels(service).take(30).joinToString(" | "))
                return Result.Failed
            }
            val target = clickableOf(node) ?: return Result.Failed.also {
                Diagnostics.add("'${tile.label}' found but not clickable")
            }
            if (!target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return Result.Failed
            if (tile.momentary) return Result.Done(null)
            delay(450)
            return Result.Done(find(service, tile.samsungLabels)?.let(::readState))
        } finally {
            if (!tile.momentary) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
            delay(150)
            busy = false
        }
    }

    sealed interface Result {
        data object Failed : Result
        data class Done(val newState: Boolean?) : Result
    }

    private fun systemUiRoots(service: AccessibilityService): List<AccessibilityNodeInfo> =
        runCatching {
            service.windows.mapNotNull { it.root }.filter { it.packageName?.contains("systemui") == true }
        }.getOrDefault(emptyList())

    private fun find(service: AccessibilityService, labels: List<String>): AccessibilityNodeInfo? {
        for (root in systemUiRoots(service)) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (matches(n, labels)) return n
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
        }
        return null
    }

    /** "Wi-Fi", "Wi-Fi, On", "Wi-Fi On" match; "Wi-Fi calling" doesn't. */
    private fun matches(n: AccessibilityNodeInfo, labels: List<String>): Boolean {
        val texts = listOfNotNull(n.contentDescription?.toString(), n.text?.toString())
        return texts.any { t ->
            labels.any { label ->
                if (!t.startsWith(label, ignoreCase = true)) return@any false
                val rest = t.substring(label.length)
                if (rest.isEmpty() || rest.startsWith(",")) return@any true
                val word = rest.trim().trimStart(',', '.', ':').trim().lowercase()
                word.isEmpty() || word.startsWith("on") || word.startsWith("off") ||
                    word.startsWith("connected") || word.startsWith("not connected")
            }
        }
    }

    private fun clickableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 6) {
            if (n.isClickable) return n
            n = n.parent
            depth++
        }
        return null
    }

    private fun readState(node: AccessibilityNodeInfo): Boolean? {
        val candidates = buildList {
            add(node)
            node.parent?.let(::add)
            for (i in 0 until node.childCount) node.getChild(i)?.let(::add)
        }
        candidates.firstOrNull { it.isCheckable }?.let { return it.isChecked }
        for (c in candidates) {
            val s = listOfNotNull(c.stateDescription?.toString(), c.contentDescription?.toString())
                .joinToString(" ").lowercase()
            if (Regex("\\b(off|disconnected|not connected)\\b").containsMatchIn(s)) return false
            if (Regex("\\b(on|connected)\\b").containsMatchIn(s)) return true
        }
        return null
    }

    private fun visibleLabels(service: AccessibilityService): List<String> {
        val out = ArrayList<String>()
        for (root in systemUiRoots(service)) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty() && out.size < 60) {
                val n = queue.removeFirst()
                val label = (n.contentDescription ?: n.text)?.toString()?.trim()
                if (!label.isNullOrEmpty() && label.length < 60) out.add(label)
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
        }
        if (out.isEmpty()) out.add("(Samsung's panel content wasn't readable)")
        return out.distinct()
    }

    private fun scroll(service: AccessibilityService) {
        for (root in systemUiRoots(service)) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (n.isScrollable && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return
                for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
            }
        }
    }
}
