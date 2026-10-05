package com.shadeui.app

import android.app.Application
import com.shadeui.app.data.RulesStore
import com.shadeui.app.data.SettingsStore
import com.shadeui.app.media.MediaRepo
import com.shadeui.app.notif.NotifRepo
import com.shadeui.app.tiles.TileStates

/** Holds the app-wide singletons shared by the services and the settings screens. */
class ShadeApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var rules: RulesStore
        private set
    val notifs = NotifRepo()
    val media = MediaRepo()
    lateinit var tiles: TileStates
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        rules = RulesStore(this)
        tiles = TileStates(this)
    }

    companion object {
        lateinit var instance: ShadeApp
            private set
    }
}
