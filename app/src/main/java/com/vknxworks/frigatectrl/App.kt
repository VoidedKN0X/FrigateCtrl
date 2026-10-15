package com.vknxworks.frigatectrl

import android.content.Context
import com.vknxworks.frigatectrl.data.FrigateApi
import com.vknxworks.frigatectrl.data.Prefs
import com.vknxworks.frigatectrl.data.Settings

/** Tiny service locator. */
object App {
    lateinit var prefs: Prefs
        private set
    lateinit var api: FrigateApi
        private set
    lateinit var settings: Settings
        private set

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(ctx.applicationContext)
        api = FrigateApi(prefs)
        settings = Settings(ctx.applicationContext)
    }
}
