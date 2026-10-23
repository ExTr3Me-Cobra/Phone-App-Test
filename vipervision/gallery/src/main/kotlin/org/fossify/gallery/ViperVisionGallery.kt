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
    fun init(app: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(app.appLockManager)
        Reprint.initialize(app)
        Picasso.setSingletonInstance(Picasso.Builder(app).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
