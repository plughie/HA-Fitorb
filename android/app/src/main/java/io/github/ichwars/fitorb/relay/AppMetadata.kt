package io.github.ichwars.fitorb.relay

import android.os.Build

const val FITORB_APP_VERSION = "0.1.8"

fun defaultRelayId(): String = "android-${Build.MODEL}".trim()
