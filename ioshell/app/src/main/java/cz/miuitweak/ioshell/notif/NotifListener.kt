package cz.miuitweak.ioshell.notif

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Cte vsechny notifikace v systemu (opravneni "Pristup k oznamenim"). */
class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        NotifRepo.listener = this
        cz.miuitweak.ioshell.media.MediaRepo.start(this)
        val list = runCatching { activeNotifications }.getOrNull().orEmpty()
        list.forEach { sbn ->
            sbn.notification.extras
                .getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession.Token::class.java)
                ?.let { cz.miuitweak.ioshell.media.MediaRepo.fromToken(this, it) }
        }
        NotifRepo.setAll(list.mapNotNull { NotifParser.parse(this, it) })
    }

    override fun onListenerDisconnected() {
        if (NotifRepo.listener === this) NotifRepo.listener = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        // banner jen pro to, co by Android ukazal jako vyskakovaci (dulezitost vysoka) a je opravdu nove
        android.util.Log.d("Banner", "posted ${sbn.packageName} ${sbn.key}")
        val prev = NotifRepo.get(sbn.key)
        onNotificationPosted(sbn)
        val r = Ranking()
        // banner pro vse, co by pipnulo (iOS ukazuje banner u kazde notifikace, ktera upozornuje)
        val important = rankingMap?.getRanking(sbn.key, r) == true &&
            r.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT
        val n = NotifRepo.get(sbn.key) ?: return
        val isNew = prev == null || prev.text != n.text || prev.title != n.title
        android.util.Log.d("Banner", "${sbn.packageName} imp=${r.importance} new=$isNew on=${cz.miuitweak.ioshell.data.Prefs.current.iosBanners}")
        if (important && isNew && cz.miuitweak.ioshell.data.Prefs.current.iosBanners) {
            NotifRepo.banners.tryEmit(n)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        sbn.notification.extras
            .getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, android.media.session.MediaSession.Token::class.java)
            ?.let { cz.miuitweak.ioshell.media.MediaRepo.fromToken(this, it) }
        val n = NotifParser.parse(this, sbn)
        if (n != null) NotifRepo.post(n) else NotifRepo.remove(sbn.key)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotifRepo.remove(sbn.key)
    }
}
