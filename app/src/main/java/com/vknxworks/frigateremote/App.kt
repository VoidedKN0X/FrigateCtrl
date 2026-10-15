package com.vknxworks.frigateremote

import android.content.Context
import com.vknxworks.frigateremote.data.FrigateApi
import com.vknxworks.frigateremote.data.Prefs
import com.vknxworks.frigateremote.data.Settings

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
