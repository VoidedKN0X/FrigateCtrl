package com.vknxworks.frigateremote

import android.content.Context
import com.vknxworks.frigateremote.data.FrigateApi
import com.vknxworks.frigateremote.data.Prefs

/** Tiny service locator. */
object App {
    lateinit var prefs: Prefs
        private set
    lateinit var api: FrigateApi
        private set

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = Prefs(ctx.applicationContext)
        api = FrigateApi(prefs)
    }
}
