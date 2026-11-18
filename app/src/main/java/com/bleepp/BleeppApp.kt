package com.bleepp

import android.app.Application
import com.bleepp.alert.AlertService
import com.bleepp.alert.Pause

class BleeppApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpdateWorker.createChannel(this)
        UpdateWorker.schedule(this)
        AlertService.createChannel(this)
        Pause.load(this)
    }
}
