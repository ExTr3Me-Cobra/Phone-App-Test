package com.shadeui.app.tiles

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** On/off state of every tile (null = unknown), plus the direct toggles. */
class TileStates(context: Context) {
    private val ctx = context.applicationContext
    private val state = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    val flow: StateFlow<Map<String, Boolean?>> = state.asStateFlow()

    /** States of Samsung-only tiles, learnt when Shade presses them. */
    private val learnt = HashMap<String, Boolean>()
    private var torchOn = false
    private val camera = ctx.getSystemService(CameraManager::class.java)

    init {
        runCatching {
            camera?.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    torchOn = enabled
                    refresh()
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    fun refresh() {
        state.value = Tiles.all.associate { it.id to runCatching { read(it.id) }.getOrNull() }
    }

    fun learn(id: String, on: Boolean?) {
        if (on != null) learnt[id] = on
        refresh()
    }

    fun soundMode(): Int = ctx.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    private fun read(id: String): Boolean? = when (id) {
        "wifi" -> ctx.getSystemService(WifiManager::class.java)?.isWifiEnabled
        "bluetooth" -> ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        "flashlight" -> torchOn
        "rotate" -> Settings.System.getInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        "airplane" -> Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        "mobile_data" -> runCatching { ctx.getSystemService(TelephonyManager::class.java)?.isDataEnabled }.getOrNull()
            ?: learnt[id]
        "dnd" -> ctx.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
            ?.let { it != NotificationManager.INTERRUPTION_FILTER_ALL }
        "sound" -> soundMode() == AudioManager.RINGER_MODE_NORMAL
        "location" -> ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled
        "power_saving" -> ctx.getSystemService(PowerManager::class.java)?.isPowerSaveMode
        "dark_mode" -> Resources.getSystem().configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        "nfc" -> NfcAdapter.getDefaultAdapter(ctx)?.isEnabled
        else -> learnt[id]
    }

    /**
     * Changes a tile the app is allowed to change. Returns a message for the user when it needs a
     * permission first, else null.
     */
    fun toggleDirect(id: String): String? {
        val current = state.value[id] ?: false
        when (id) {
            "flashlight" -> {
                val cam = camera ?: return "No flashlight found"
                val camId = cam.cameraIdList.firstOrNull {
                    cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return "No flashlight found"
                runCatching { cam.setTorchMode(camId, !torchOn) }
            }
            "rotate" -> {
                if (!Settings.System.canWrite(ctx)) return NEED_WRITE_SETTINGS
                Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (current) 0 else 1)
            }
            "dnd" -> {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) return NEED_DND
                nm.setInterruptionFilter(
                    if (current) NotificationManager.INTERRUPTION_FILTER_ALL
                    else NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                )
            }
            "sound" -> {
                val am = ctx.getSystemService(AudioManager::class.java)
                // Sound -> Vibrate -> Mute -> Sound, like Samsung's tile.
                val next = when (am.ringerMode) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                runCatching { am.ringerMode = next }.onFailure {
                    if (next == AudioManager.RINGER_MODE_SILENT) {
                        runCatching { am.ringerMode = AudioManager.RINGER_MODE_NORMAL }
                    }
                }
            }
        }
        refresh()
        return null
    }

    companion object {
        const val NEED_WRITE_SETTINGS = "Allow \"Modify system settings\" for Shade in its setup screen."
        const val NEED_DND = "Allow \"Do not disturb access\" for Shade in its setup screen."
    }
}
