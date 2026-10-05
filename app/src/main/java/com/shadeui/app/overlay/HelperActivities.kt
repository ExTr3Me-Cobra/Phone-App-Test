package com.shadeui.app.overlay

import android.app.Activity
import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import com.shadeui.app.ShadeApp

/**
 * Opens a notification or action: unlocks first if the phone is locked (showing the PIN /
 * fingerprint prompt), then fires the app's PendingIntent and disappears.
 */
class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pi = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_PI, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_PI)
        }
        val key = intent.getStringExtra(EXTRA_KEY)
        val cancel = intent.getBooleanExtra(EXTRA_CANCEL, false)
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = fire(pi, key, cancel)
                override fun onDismissCancelled() = finish()
                override fun onDismissError() = finish()
            })
        } else {
            fire(pi, key, cancel)
        }
    }

    private fun fire(pi: PendingIntent?, key: String?, cancel: Boolean) {
        if (pi != null) send(this, pi, null)
        if (cancel && key != null) com.shadeui.app.notif.NotifListener.instance?.dismiss(key)
        finish()
    }

    companion object {
        private const val EXTRA_PI = "pi"
        private const val EXTRA_KEY = "key"
        private const val EXTRA_CANCEL = "cancel"

        fun open(context: Context, pi: PendingIntent?, key: String? = null, cancelAfter: Boolean = false) {
            context.startActivity(
                Intent(context, TrampolineActivity::class.java)
                    .putExtra(EXTRA_PI, pi)
                    .putExtra(EXTRA_KEY, key)
                    .putExtra(EXTRA_CANCEL, cancelAfter)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }

        /** Sends another app's PendingIntent, allowed to start its activity from here. */
        fun send(context: Context, pi: PendingIntent, fillIn: Intent?) {
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            runCatching { pi.send(context, 0, fillIn, null, null, null, options.toBundle()) }
        }
    }
}

/**
 * Turns the screen on for a new notification (lock-screen edge lighting), then, if you didn't
 * touch anything, turns it off again like Samsung's own edge lighting.
 */
class WakeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var touched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val seconds = intent.getIntExtra(EXTRA_SECONDS, 6)
        handler.postDelayed({ done() }, seconds * 1000L)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        touched = true
        finish()
        return true
    }

    override fun onUserLeaveHint() {
        touched = true
        finish()
    }

    private fun done() {
        if (isFinishing) return
        finish()
        val s = ShadeApp.instance.settings.value
        val km = getSystemService(KeyguardManager::class.java)
        if (!touched && s.screenOffAfterWake && km.isKeyguardLocked) {
            OverlayService.instance?.lockScreenNow()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SECONDS = "seconds"

        fun start(context: Context, seconds: Int) {
            context.startActivity(
                Intent(context, WakeActivity::class.java)
                    .putExtra(EXTRA_SECONDS, seconds)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
    }
}
