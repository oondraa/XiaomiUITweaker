package cz.miuitweak.ioshell.airpods

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stav AirPods z Bluetooth vysilani Apple "proximity pairing" (to same cte iPhone). */
data class AirPodsState(
    val model: String,
    /** 0..100 po 10 %, null = nezname (napr. sluchatko neni v pouzdre / mimo dosah) */
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val chargingLeft: Boolean,
    val chargingRight: Boolean,
    val chargingCase: Boolean,
    val rssi: Int,
    val seenAt: Long = SystemClock.elapsedRealtime(),
    /** celkova baterie z Bluetooth pripojeni (kdyz neni rozpis L/P/pouzdro) */
    val combined: Int? = null,
) {
    val fresh: Boolean get() = SystemClock.elapsedRealtime() - seenAt < 120_000
}

object AirPods {
    private const val APPLE = 76
    /** jen AirPods v blizkosti (cizi AirPods v MHD nas nezajimaji) */
    private const val NEAR_RSSI = -62

    private val _state = MutableStateFlow<AirPodsState?>(null)
    val state: StateFlow<AirPodsState?> get() = _state

    /** "pouzdro se prave otevrelo u telefonu" -> okno s baterii */
    val popups = MutableSharedFlow<AirPodsState>(extraBufferCapacity = 2)

    private var lastSeen = 0L
    private var lastPopup = 0L
    private var scanning = false
    private var ctx: Context? = null

    private val cb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(it) }
    }

    private const val ACTION_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    private const val EXTRA_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private val btReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                        scanning = false
                        start(context)
                    }
                // AirPods se pripojily (vyndane z pouzdra) -> okno jako na iPhonu
                android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val d = intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                    if (d != null && isAirPods(d)) main.postDelayed({ onConnected(d) }, 1500)
                }
                ACTION_BATTERY -> {
                    val d = intent.getParcelableExtra(android.bluetooth.BluetoothDevice.EXTRA_DEVICE, android.bluetooth.BluetoothDevice::class.java)
                    val lvl = intent.getIntExtra(EXTRA_BATTERY, -1)
                    if (d != null && isAirPods(d) && lvl in 0..100) updateCombined(d, lvl)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun isAirPods(d: android.bluetooth.BluetoothDevice) =
        runCatching { d.name?.contains("AirPods", ignoreCase = true) == true }.getOrDefault(false)

    /** Baterie pripojeneho zarizeni (skryte API Androidu, ale funguje). */
    private fun batteryOf(d: android.bluetooth.BluetoothDevice): Int? = runCatching {
        (d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int).takeIf { it in 0..100 }
    }.getOrNull()

    @SuppressLint("MissingPermission")
    private fun updateCombined(d: android.bluetooth.BluetoothDevice, lvl: Int) {
        val cur = _state.value
        _state.value = if (cur != null && cur.left != null) cur.copy(combined = lvl, seenAt = SystemClock.elapsedRealtime())
        else AirPodsState(
            model = runCatching { d.name }.getOrNull() ?: "AirPods",
            left = null, right = null, case = null,
            chargingLeft = false, chargingRight = false, chargingCase = false,
            rssi = 0, combined = lvl,
        )
    }

    private fun onConnected(d: android.bluetooth.BluetoothDevice) {
        val lvl = batteryOf(d)
        if (lvl != null) updateCombined(d, lvl)
        val s = _state.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPopup > 60_000) {
            lastPopup = now
            popups.tryEmit(s)
        }
    }

    /** Pri startu: kdyz uz jsou AirPods pripojene, nacist jejich baterii. */
    @SuppressLint("MissingPermission")
    private fun readConnected(c: Context) {
        val adapter = c.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        runCatching {
            adapter.bondedDevices.filter { isAirPods(it) }.forEach { d ->
                val connected = runCatching { d.javaClass.getMethod("isConnected").invoke(d) as Boolean }.getOrDefault(false)
                val lvl = batteryOf(d)
                if (connected && lvl != null) updateCombined(d, lvl)
            }
        }
    }

    fun init(c: Context) {
        if (ctx != null) return
        ctx = c.applicationContext
        ContextCompat.registerReceiver(
            c.applicationContext, btReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(ACTION_BATTERY)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        readConnected(c)
    }

    @SuppressLint("MissingPermission")
    fun start(c: Context) {
        init(c)
        if (scanning) return
        val scanner = c.getSystemService(BluetoothManager::class.java)?.adapter
            ?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        // bez filtru v cipu (na nekterych telefonech filtr na data vyrobce nevraci nic) – filtrujeme sami;
        // sken bezi jen pri rozsvicenem displeji, kdy Android sken bez filtru povoli
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        runCatching {
            scanner.startScan(null, settings, cb)
            scanning = true
        }.onFailure { android.util.Log.w("AirPods", "scan failed", it) }
    }

    @SuppressLint("MissingPermission")
    fun stop(c: Context) {
        if (!scanning) return
        runCatching {
            c.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
        }
        scanning = false
    }

    private fun handle(r: ScanResult) {
        val data = r.scanRecord?.getManufacturerSpecificData(APPLE) ?: return
        if (data.isEmpty() || data[0] != 0x07.toByte()) return
        android.util.Log.d("AirPods", "ad rssi=${r.rssi} len=${data.size} ${data.joinToString("") { "%02x".format(it) }}")
        if (r.rssi < NEAR_RSSI) return
        val s = parse(data, r.rssi)?.let { p -> p.copy(combined = _state.value?.combined) } ?: return
        val now = SystemClock.elapsedRealtime()
        // novy "vyskyt" po chvili ticha + pouzdro hlasi baterii (= je otevrene) -> okno jako na iPhonu
        val reappeared = now - lastSeen > 25_000
        lastSeen = now
        _state.value = s
        if (reappeared && s.case != null && now - lastPopup > 60_000) {
            lastPopup = now
            popups.tryEmit(s)
        }
    }

    /**
     * Rozlozeni dat (podle OpenPods/CAPod): bajt0 = 0x07, bajt3-4 = model, bajt5 = stav (bit 0x20 = prohozena strana),
     * bajt6 = baterie sluchatek (2 pulbajty), bajt7 = nabijeni (horni pulbajt) + pouzdro (dolni).
     * Hodnota 0..10 = 0..100 %, 15 = nezname.
     */
    fun parse(b: ByteArray, rssi: Int): AirPodsState? {
        if (b.size < 9 || b[0] != 0x07.toByte()) return null
        val hex = b.joinToString("") { "%02x".format(it) }
        fun nib(i: Int) = hex[i].digitToInt(16)
        fun pct(v: Int) = if (v in 0..10) v * 10 else null
        val flip = (nib(10) and 0x02) == 0
        val left = pct(nib(if (flip) 12 else 13))
        val right = pct(nib(if (flip) 13 else 12))
        val case = pct(nib(15))
        val ch = nib(14)
        if (left == null && right == null && case == null) return null
        val model = ((b[3].toInt() and 0xFF) shl 8) or (b[4].toInt() and 0xFF)
        return AirPodsState(
            model = modelName(model),
            left = left,
            right = right,
            case = case,
            chargingLeft = ch and (if (flip) 0b10 else 0b01) != 0,
            chargingRight = ch and (if (flip) 0b01 else 0b10) != 0,
            chargingCase = ch and 0b100 != 0,
            rssi = rssi,
        )
    }

    private fun modelName(m: Int) = when (m) {
        0x0220 -> "AirPods"
        0x0F20 -> "AirPods (2. gen.)"
        0x1320 -> "AirPods (3. gen.)"
        0x1920, 0x1B20 -> "AirPods 4"
        0x0E20 -> "AirPods Pro"
        0x1420, 0x2420 -> "AirPods Pro 2"
        0x0A20, 0x1F20 -> "AirPods Max"
        else -> "AirPods Pro"
    }
}
