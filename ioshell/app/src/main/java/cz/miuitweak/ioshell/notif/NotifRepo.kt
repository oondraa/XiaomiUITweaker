package cz.miuitweak.ioshell.notif

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Vsechny notifikace + logika "videno": co bylo na zamykacce a pak se odemklo,
 * uz se na zamykacce neukazuje a je jen v historii (Centrum oznameni) – jako na iOS.
 */
object NotifRepo {
    private val raw = LinkedHashMap<String, Notif>()
    /** klic -> cas notifikace, kterou uz uzivatel videl */
    private val seen = HashMap<String, Long>()
    /** balicek -> do kdy je ztlumeny */
    private val muted = HashMap<String, Long>()

    private val _items = MutableStateFlow<List<Notif>>(emptyList())
    val items: StateFlow<List<Notif>> get() = _items

    /** nove (ne jen aktualizovane) dulezite notifikace -> iOS banner */
    val banners = kotlinx.coroutines.flow.MutableSharedFlow<Notif>(extraBufferCapacity = 8)

    @Synchronized
    fun get(key: String): Notif? = raw[key]

    @Volatile
    var listener: NotifListener? = null

    val connected: Boolean get() = listener != null

    @Synchronized
    fun setAll(list: List<Notif>) {
        raw.clear()
        list.forEach { raw[it.key] = it }
        publish()
    }

    @Synchronized
    fun post(n: Notif) {
        raw[n.key] = n
        publish()
    }

    @Synchronized
    fun remove(key: String) {
        if (raw.remove(key) != null) publish()
    }

    @Synchronized
    fun markAllSeen() {
        raw.values.forEach { seen[it.key] = it.time }
        publish()
    }

    @Synchronized
    fun mute(pkg: String, millis: Long) {
        muted[pkg] = System.currentTimeMillis() + millis
        publish()
    }

    fun dismiss(n: Notif) {
        if (!n.clearable) return
        listener?.cancelNotification(n.key)
        remove(n.key)
    }

    fun dismissAll(list: List<Notif>) {
        val keys = list.filter { it.clearable }.map { it.key }
        if (keys.isEmpty()) return
        listener?.cancelNotifications(keys.toTypedArray())
        synchronized(this) {
            keys.forEach { raw.remove(it) }
            publish()
        }
    }

    /** Otevre notifikaci (musi se volat az po odemceni). */
    fun open(ctx: Context, n: Notif) {
        runCatching { n.contentIntent?.send() }
        if (n.autoCancel) dismiss(n)
    }

    fun runAction(ctx: Context, a: NotifAction, reply: String? = null) {
        val ri = a.remoteInput
        if (ri != null && reply != null) {
            val fill = Intent()
            RemoteInput.addResultsToIntent(arrayOf(ri), fill, Bundle().apply { putCharSequence(ri.resultKey, reply) })
            runCatching { a.intent?.send(ctx, 0, fill) }
        } else {
            runCatching { a.intent?.send() }
        }
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        muted.entries.removeAll { it.value < now }
        _items.value = raw.values
            .filter { it.pkg !in muted }
            .map { it.copy(seen = (seen[it.key] ?: Long.MIN_VALUE) >= it.time) }
            .sortedByDescending { it.time }
    }
}
