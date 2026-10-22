package io.github.sammeeeeeeee.onenet

import android.app.Application
import io.github.sammeeeeeeee.onenet.shell.Shell

class OneNetApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.init()
    }
}
