package cz.miuitweak.ioshell

import android.app.Application
import cz.miuitweak.ioshell.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        // Po aktualizaci/restartu MIUI obcas zapomene sluzbu notifikaci pripojit -> pozadat o to.
        runCatching {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, cz.miuitweak.ioshell.notif.NotifListener::class.java),
            )
        }
    }
}
