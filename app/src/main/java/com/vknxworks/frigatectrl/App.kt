package com.vknxworks.frigatectrl

import android.content.Context
import androidx.annotation.StringRes
import com.vknxworks.frigatectrl.data.FrigateApi
import com.vknxworks.frigatectrl.data.LockSettings
import com.vknxworks.frigatectrl.data.Prefs
import com.vknxworks.frigatectrl.data.Settings
import com.vknxworks.frigatectrl.data.lock.LockVault

/** Tiny service locator. */
object App {
    lateinit var prefs: Prefs
        private set
    lateinit var api: FrigateApi
        private set
    lateinit var settings: Settings
        private set
    lateinit var lock: LockSettings
        private set
    /** The app lock's PIN, fingerprint key and the key that encrypts the login while locked. */
    lateinit var vault: LockVault
        private set

    private lateinit var appContext: Context

    /** Text from strings.xml for code that has no Context of its own (errors from the data layer). */
    fun str(@StringRes id: Int, vararg args: Any): String = appContext.getString(id, *args)

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        appContext = ctx.applicationContext
        vault = LockVault(ctx.applicationContext, "frigatectrl")
        prefs = Prefs(ctx.applicationContext, vault)
        api = FrigateApi(prefs)
        settings = Settings(ctx.applicationContext)
        lock = LockSettings(ctx.applicationContext)
    }
}
