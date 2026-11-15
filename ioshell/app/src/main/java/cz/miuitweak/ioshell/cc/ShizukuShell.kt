package cz.miuitweak.ioshell.cc

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Spousti prikazy jako "shell" uzivatel pres Shizuku (bez rootu).
 * Diky tomu jde opravdu prepnout Wi-Fi / BT / data, coz bezna appka od Androidu 10 nesmi.
 */
object ShizukuShell {
    const val REQUEST_CODE = 4711

    val running: Boolean get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val granted: Boolean
        get() = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun requestPermission() {
        if (running && !granted) runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    // Shizuku.newProcess je v API 13 skryte, ale porad funguje – volame ho pres reflexi.
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }

    /** Vrati true, kdyz prikaz probehl s navratovym kodem 0. */
    suspend fun run(cmd: String): Boolean = withContext(Dispatchers.IO) {
        if (!granted) return@withContext false
        runCatching {
            val p = newProcess.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            p.inputStream.readBytes()
            p.waitFor() == 0
        }.getOrDefault(false)
    }
}
