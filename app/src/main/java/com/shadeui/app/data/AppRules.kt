package com.shadeui.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** How a new notification from an app announces itself. */
enum class AlertMode {
    /** Like Android: pops up if the app marked it urgent, otherwise arrives quietly. */
    FOLLOW_APP,

    /** Always pops up on screen. */
    POPUP,

    /** Never pops up or lights up; just appears in the panel. */
    SILENT,

    /** Not shown anywhere by Shade. */
    HIDDEN,
}

enum class LockVisibility { SHOW, HIDE_CONTENT, HIDE }

/** Three-way switch: use the global default, or force on/off for this app. */
enum class Tri { DEFAULT, ON, OFF;

    fun resolve(default: Boolean) = when (this) {
        DEFAULT -> default
        ON -> true
        OFF -> false
    }
}

data class AppRule(
    val alert: AlertMode = AlertMode.FOLLOW_APP,
    val popupStyle: PopupStyle? = null,
    val lockScreen: LockVisibility = LockVisibility.SHOW,
    val lightLocked: Tri = Tri.DEFAULT,
    val wakeScreen: Tri = Tri.DEFAULT,
    val lightUnlocked: Tri = Tri.DEFAULT,
    /** Null = automatic (per the global colour setting). */
    val lightColor: Int? = null,
) {
    val isDefault get() = this == AppRule()
}

/** Per-app rules, stored as JSON keyed by package name. */
class RulesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rules", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<Map<String, AppRule>> = state.asStateFlow()

    fun get(pkg: String): AppRule = state.value[pkg] ?: AppRule()

    fun set(pkg: String, rule: AppRule) {
        val next = state.value.toMutableMap()
        if (rule.isDefault) next.remove(pkg) else next[pkg] = rule
        state.value = next
        prefs.edit().apply {
            if (rule.isDefault) remove(pkg) else putString(pkg, toJson(rule))
        }.apply()
    }

    private fun load(): Map<String, AppRule> = prefs.all.mapNotNull { (pkg, value) ->
        (value as? String)?.let { json -> runCatching { pkg to fromJson(json) }.getOrNull() }
    }.toMap()

    private fun toJson(r: AppRule) = JSONObject()
        .put("alert", r.alert.name)
        .put("popupStyle", r.popupStyle?.name ?: "")
        .put("lockScreen", r.lockScreen.name)
        .put("lightLocked", r.lightLocked.name)
        .put("wakeScreen", r.wakeScreen.name)
        .put("lightUnlocked", r.lightUnlocked.name)
        .apply { r.lightColor?.let { put("lightColor", it) } }
        .toString()

    private fun fromJson(json: String): AppRule {
        val o = JSONObject(json)
        return AppRule(
            alert = AlertMode.valueOf(o.optString("alert", AlertMode.FOLLOW_APP.name)),
            popupStyle = o.optString("popupStyle").takeIf { it.isNotEmpty() }?.let { PopupStyle.valueOf(it) },
            lockScreen = LockVisibility.valueOf(o.optString("lockScreen", LockVisibility.SHOW.name)),
            lightLocked = Tri.valueOf(o.optString("lightLocked", Tri.DEFAULT.name)),
            wakeScreen = Tri.valueOf(o.optString("wakeScreen", Tri.DEFAULT.name)),
            lightUnlocked = Tri.valueOf(o.optString("lightUnlocked", Tri.DEFAULT.name)),
            lightColor = if (o.has("lightColor")) o.getInt("lightColor") else null,
        )
    }
}
