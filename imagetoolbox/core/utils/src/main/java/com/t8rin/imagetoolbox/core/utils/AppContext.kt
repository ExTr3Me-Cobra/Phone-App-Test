/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.imagetoolbox.core.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.StringRes
import androidx.core.content.getSystemService
import com.t8rin.imagetoolbox.core.resources.R

class AppContext private constructor(
    application: Context
) : ContextWrapper(application) {

    companion object {
        internal var appContext: AppContext? = null

        internal fun init(application: Context) {
            if (appContext == null) {
                appContext = AppContext(application)
            } else {
                "AppContext is already initialized".makeLog("AppContext")
            }
        }
    }

}

fun Context.initAppContext() = AppContext.init(applicationContext)

val appContext: AppContext
    get() = checkNotNull(AppContext.appContext) {
        "AppContext not initialized"
    }

/**
 * Viper Vision: the installed app's own version (e.g. "1.0.0 build 132"), not Image Toolbox's
 * library version that BuildConfig.VERSION_NAME carries.
 */
val installedVersionName: String by lazy {
    runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }
        .getOrNull() ?: "?"
}

val installedVersionCode: Long by lazy {
    runCatching {
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        )
    }.getOrDefault(0L)
}

fun getString(@StringRes resId: Int): String = appContext.getString(resId)

fun getString(
    @StringRes resId: Int,
    vararg formatArgs: Any?
): String = appContext.getString(resId, *formatArgs)

fun Throwable.extractMessage(): String = if (this is OutOfMemoryError) {
    getString(R.string.oom_description)
} else {
    getString(
        R.string.smth_went_wrong,
        (localizedMessage?.takeIf { it.isNotBlank() } ?: message)?.decodeEscaped().orEmpty()
            .ifEmpty {
                this::class.java.simpleName
            }
    )
}

@SuppressLint("MissingPermission")
fun Context.isNetworkAvailable(): Boolean {
    return getSystemService<ConnectivityManager>()?.run {
        val capabilities = getNetworkCapabilities(
            activeNetwork ?: return false
        ) ?: return false

        // Viper Vision: VPNs often never get Android's "validated" mark even though the
        // internet works through them, so only require a network that can reach the internet;
        // a real failure still shows its own error when the download is tried.
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } ?: false
}