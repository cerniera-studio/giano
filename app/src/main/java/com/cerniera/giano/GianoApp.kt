package com.cerniera.giano

import android.app.Application
import com.cerniera.kit.Crash

class GianoApp : Application() {
    override fun onCreate() { super.onCreate(); Crash.install(this, "Giano") }
}
