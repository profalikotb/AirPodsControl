package com.profalikotb.airpods

import android.app.Application
import com.profalikotb.airpods.bt.LinkManager
import com.profalikotb.airpods.bt.receiverScope
import com.profalikotb.airpods.data.Prefs
import com.profalikotb.airpods.notify.Notifier
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        receiverScope.launch {
            Notifier.createChannels(this@App, Prefs.get(this@App).current().lang)
            // Recover after process death / app update: re-derive connection, (re)arm or stop the scan.
            LinkManager.onEvent(this@App, null)
        }
    }
}
