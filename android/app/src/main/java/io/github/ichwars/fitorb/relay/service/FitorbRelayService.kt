package io.github.ichwars.fitorb.relay.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class FitorbRelayService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
