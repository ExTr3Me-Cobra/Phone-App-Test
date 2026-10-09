package com.glowalerts.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * An invisible screen that doesn't take touches or the keyboard. Android sometimes stops drawing
 * for apps in the background; having this open for a moment counts as the app being in front,
 * so the lighting shows. It closes as soon as the lighting is on screen.
 */
class BoostActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        )
        current = this
        main.removeCallbacks(closer)
        main.postDelayed(closer, MAX_MS)
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        private const val MAX_MS = 2_000L
        private val main = Handler(Looper.getMainLooper())
        private var current: BoostActivity? = null
        private val closer = Runnable { close() }

        fun open(context: Context) {
            if (current != null) return
            runCatching {
                context.startActivity(
                    Intent(context, BoostActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                            Intent.FLAG_ACTIVITY_NO_USER_ACTION,
                    ),
                )
            }.onFailure { Log.add("Lighting: couldn't bring the app to the front (${it.message})") }
        }

        fun close() {
            main.removeCallbacks(closer)
            current?.let {
                it.finish()
                it.overridePendingTransition(0, 0)
            }
            current = null
        }
    }
}
