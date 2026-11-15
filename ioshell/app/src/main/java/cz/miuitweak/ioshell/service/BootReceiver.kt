package cz.miuitweak.ioshell.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cz.miuitweak.ioshell.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.current.enabled) LockService.start(context)
    }
}
