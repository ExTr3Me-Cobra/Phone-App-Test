package org.fossify.gallery

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.extensions.appLockManager

/**
 * Viper Vision has a single Application class (Image Toolbox's), so Gallery's App.kt never runs.
 * This does the same startup work as App.kt and FossifyApp; the app calls it from onCreate.
 */
object ViperVisionGallery {

    /**
     * Set each time the app comes to the front (opened from the launcher or app list, or switched
     * back to); the albums screen then checks the AI models once. Only screens count, so the
     * screenshot window (an overlay) never sets it.
     */
    @Volatile
    var cameToFront = false

    fun init(app: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(app.appLockManager)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                cameToFront = true
            }
        })
        Reprint.initialize(app)
        Picasso.setSingletonInstance(Picasso.Builder(app).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
