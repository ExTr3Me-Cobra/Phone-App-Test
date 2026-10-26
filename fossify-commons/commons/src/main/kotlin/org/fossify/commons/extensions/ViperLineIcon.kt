package org.fossify.commons.extensions

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ComponentEnabledSetting
import android.os.Build
import android.os.Bundle
import android.util.Log
import org.fossify.commons.helpers.LAUNCHER_ALIAS_PACKAGE
import org.fossify.commons.helpers.appIconColorStrings

/**
 * Viper Vision: the line-art launcher icon (Customise colours → Line icon). While it's on, the
 * launcher shows the "SplashActivity.Line" alias and the colour aliases stay off; the icon colour
 * setting is kept and comes back when it's turned off.
 */
var Context.isLineIconEnabled: Boolean
    get() = getSharedPrefs().getBoolean(LINE_ICON_KEY, false)
    set(value) = getSharedPrefs().edit().putBoolean(LINE_ICON_KEY, value).apply()

/** Line icon on a transparent background instead of black. */
var Context.isLineIconTransparent: Boolean
    get() = getSharedPrefs().getBoolean(LINE_ICON_CLEAR_KEY, false)
    set(value) = getSharedPrefs().edit().putBoolean(LINE_ICON_CLEAR_KEY, value).apply()

/**
 * Shows the line icon or the coloured one (Customise colours → App icon colour) in the launcher,
 * to match [isLineIconEnabled]. Closes the app when the entry it was opened from goes off, so call
 * [applyLineIconWhenLeaving] from a screen instead.
 */
fun Context.applyLineIcon() {
    val appId = baseConfig.appId.ifEmpty { packageName }
    val colors = getAppIconColors()
    val colorIndex = colors.indexOf(baseConfig.appIconColor).takeIf { it >= 0 }
        ?: appIconColorStrings.indexOf(".Green")
    val wanted = when {
        !isLineIconEnabled -> appIconColorStrings[colorIndex]
        isLineIconTransparent -> LINE_CLEAR_SUFFIX
        else -> LINE_SUFFIX
    }
    Log.w(TAG, "Launcher icon → SplashActivity$wanted (line=$isLineIconEnabled, transparent=$isLineIconTransparent, colour index $colorIndex, app $appId)")
    baseConfig.lastIconColor = baseConfig.appIconColor
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // All in one go: Android can close the app partway through separate calls, which once left
        // two launcher icons on (the new one turned on, the old one never turned off).
        val changes = ALL_ALIASES.map { suffix ->
            ComponentEnabledSetting(
                aliasComponent(appId, suffix),
                if (suffix == wanted) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
        runCatching { packageManager.setComponentEnabledSettings(changes) }
            .onFailure { Log.w(TAG, "Couldn't switch the launcher icon", it) }
    } else {
        // Turn the wanted one on first, so there's never a moment with no launcher icon.
        setAlias(appId, wanted, true)
        ALL_ALIASES.filter { it != wanted }.forEach { setAlias(appId, it, false) }
    }
    Log.w(TAG, "Launcher icon switched; enabled now: ${enabledAliases(appId)}")
}

/** The launcher entries that are on right now (more than one means more than one icon). */
fun Context.enabledAliases(appId: String = baseConfig.appId.ifEmpty { packageName }): List<String> =
    ALL_ALIASES.filter { suffix ->
        val state = runCatching { packageManager.getComponentEnabledSetting(aliasComponent(appId, suffix)) }
            .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
        // Green is the one the manifest turns on by default.
        state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && suffix == DEFAULT_ALIAS)
    }

/**
 * If an earlier switch was cut short and left two (or more) launcher icons, puts it right once
 * the app is left. Call when the app starts.
 */
fun Activity.fixDuplicateLauncherIcons() {
    val on = enabledAliases()
    if (on.size > 1) {
        Log.w(TAG, "More than one launcher icon is on ($on); fixing it when the app is left")
        applyLineIconWhenLeaving()
    }
}

private fun aliasComponent(appId: String, suffix: String) =
    ComponentName(appId, "$LAUNCHER_ALIAS_PACKAGE.activities.SplashActivity$suffix")

private fun Context.setAlias(appId: String, suffix: String, enabled: Boolean) {
    val className = "$LAUNCHER_ALIAS_PACKAGE.activities.SplashActivity$suffix"
    val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    runCatching {
        packageManager.setComponentEnabledSetting(ComponentName(appId, className), state, PackageManager.DONT_KILL_APP)
    }.onFailure {
        // Warnings reach Settings → Diagnostic log (its system section keeps W/E lines).
        Log.w(TAG, "Couldn't ${if (enabled) "enable" else "disable"} $className in $appId", it)
    }
}

/**
 * Switches the launcher icon once the app is no longer on screen, instead of right away.
 *
 * Switching turns the launcher entry the app was opened from off, and Android closes the app when
 * that happens (on Samsung phones it looked like a crash the moment the tick box was tapped).
 * Done after the last screen of the app is closed or sent to the background, nothing is lost.
 */
fun Activity.applyLineIconWhenLeaving() {
    val app = application
    pendingIconWatcher?.let { app.unregisterActivityLifecycleCallbacks(it) }
    val watcher = object : Application.ActivityLifecycleCallbacks {
        // This screen is the only one started right now.
        private var started = 1

        override fun onActivityStarted(activity: Activity) {
            started++
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity.isChangingConfigurations) return
            started--
            if (started <= 0) {
                Log.w(TAG, "Left the app; switching the launcher icon now")
                app.unregisterActivityLifecycleCallbacks(this)
                pendingIconWatcher = null
                app.applyLineIcon()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
    pendingIconWatcher = watcher
    app.registerActivityLifecycleCallbacks(watcher)
}

private var pendingIconWatcher: Application.ActivityLifecycleCallbacks? = null

/** The line icon's launcher image (for the preview in Customise colours), or 0. */
fun Context.lineIconResId(transparent: Boolean = isLineIconTransparent): Int =
    resources.getIdentifier(if (transparent) "ic_launcher_line_clear" else "ic_launcher_line", "mipmap", packageName)

private const val LINE_ICON_KEY = "vv_line_icon"
private const val TAG = "ViperIcon"
private const val LINE_SUFFIX = ".Line"
private const val DEFAULT_ALIAS = ".Green"
private const val LINE_CLEAR_SUFFIX = ".LineClear"
private const val LINE_ICON_CLEAR_KEY = "vv_line_icon_clear"
private val ALL_ALIASES get() = appIconColorStrings + LINE_SUFFIX + LINE_CLEAR_SUFFIX
