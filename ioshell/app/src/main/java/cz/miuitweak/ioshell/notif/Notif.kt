package cz.miuitweak.ioshell.notif

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class NotifAction(
    val title: String,
    val intent: PendingIntent?,
    /** Kdyz neni null, akce umi odpoved textem (Odpovedet). */
    val remoteInput: RemoteInput?,
)

@Immutable
data class Notif(
    val key: String,
    val pkg: String,
    val appName: String,
    val appIcon: ImageBitmap?,
    /** Avatar odesilatele / nahled obrazku (largeIcon). */
    val avatar: ImageBitmap?,
    val title: String,
    val text: String,
    val bigText: String,
    val time: Long,
    /** Skupina jako na iOS "Automaticky": konverzace/vlakno, jinak cela aplikace. */
    val group: String,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
    val actions: List<NotifAction>,
    val seen: Boolean = false,
)

data class NotifGroup(val key: String, val items: List<Notif>) {
    val top: Notif get() = items.first()
}

/** Seskupi notifikace (uz serazene od nejnovejsi) a zachova poradi podle nejnovejsi v kazde skupine. */
fun List<Notif>.grouped(): List<NotifGroup> =
    groupBy { it.group }.map { (k, v) -> NotifGroup(k, v) }

object NotifParser {
    private val iconCache = HashMap<String, ImageBitmap?>()
    private val labelCache = HashMap<String, String>()

    fun parse(ctx: Context, sbn: StatusBarNotification): Notif? {
        val n = sbn.notification
        val ex = n.extras
        if (sbn.packageName == ctx.packageName) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        // trvale notifikace (hudba, bezici sluzby, navigace) na zamykacku nepatri – hudba bude zvlast
        if (sbn.isOngoing) return null
        // hudba/video ma vlastni prehravac (i pozastavena, kdy uz neni "ongoing")
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null

        var title = (ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: ex.getCharSequence(Notification.EXTRA_TITLE))?.toString().orEmpty()
        var text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        var big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: text

        val msgs = runCatching {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                ex.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java),
            )
        }.getOrNull().orEmpty()
        // "ja" v konverzaci (napr. tvuj IG ucet) – nesmi se ukazovat jako odesilatel
        val selfName = (
            ex.getParcelable(Notification.EXTRA_MESSAGING_PERSON, android.app.Person::class.java)?.name
                ?: ex.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)
            )?.toString()
        if (msgs.isNotEmpty()) {
            val isGroupChat = ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION)

            @Suppress("DEPRECATION")
            fun sender(m: Notification.MessagingStyle.Message): String? =
                (m.senderPerson?.name ?: m.sender)?.toString()?.takeIf { it.isNotBlank() && it != selfName }
            fun line(m: Notification.MessagingStyle.Message): String {
                val who = sender(m)
                return if (isGroupChat && who != null) "$who: ${m.text}" else m.text.toString()
            }
            text = line(msgs.last())
            big = msgs.takeLast(6).joinToString("\n") { line(it) }
            val lastSender = msgs.asReversed().firstNotNullOfOrNull { sender(it) }
            val convTitle = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            // 1:1 chat = jmeno toho, kdo pise; skupina = nazev skupiny
            title = if (isGroupChat) convTitle ?: lastSender ?: title else lastSender ?: convTitle ?: title
        }
        if (selfName != null && title == selfName) title = ""

        // docasne: co presne posila Instagram (kvuli ladeni jmen)
        if (sbn.packageName == "com.instagram.android") {
            android.util.Log.d(
                "NotifParser",
                "self=$selfName msgs=${msgs.size} " +
                    ex.keySet().joinToString { k -> "$k=${ex.get(k)?.toString()?.take(60)}" },
            )
        }
        if (title.isBlank() && text.isBlank()) return null

        val pkg = sbn.packageName
        val appName = labelCache.getOrPut(pkg) {
            runCatching {
                val pm = ctx.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
        }
        if (title.isBlank()) title = appName
        val appIcon = iconCache.getOrPut(pkg) {
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(108, 108).asImageBitmap() }.getOrNull()
        }
        // fotka cloveka jen u zprav/hovoru, jinak ikona aplikace (lajky z IG, GitHub…)
        val conversation = msgs.isNotEmpty() || n.category == Notification.CATEGORY_MESSAGE ||
            n.category == Notification.CATEGORY_CALL
        val avatar = if (!conversation) null else runCatching {
            n.getLargeIcon()?.loadDrawable(ctx)?.toBitmap(120, 120)?.asImageBitmap()
        }.getOrNull()

        val thread = n.shortcutId ?: n.group ?: ""
        return Notif(
            key = sbn.key,
            pkg = pkg,
            appName = appName,
            appIcon = appIcon,
            avatar = avatar,
            title = title,
            text = text,
            bigText = big,
            time = if (n.`when` > 0) n.`when` else sbn.postTime,
            group = "$pkg|$thread",
            contentIntent = n.contentIntent,
            autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0,
            clearable = sbn.isClearable,
            actions = n.actions.orEmpty().map { a ->
                NotifAction(
                    title = a.title?.toString().orEmpty(),
                    intent = a.actionIntent,
                    remoteInput = a.remoteInputs?.firstOrNull { it.allowFreeFormInput },
                )
            },
        )
    }
}
