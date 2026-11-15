package cz.miuitweak.ioshell.lock

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Svitilna – stav sleduje i zmeny z jinych mist (ovladaci centrum MIUI apod.). */
object Torch {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> get() = _on

    private var cm: CameraManager? = null
    private var camId: String? = null

    fun init(ctx: Context) {
        if (cm != null) return
        val m = ctx.applicationContext.getSystemService(CameraManager::class.java) ?: return
        cm = m
        camId = runCatching {
            m.cameraIdList.firstOrNull {
                m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        m.registerTorchCallback(object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camId) _on.value = enabled
            }
        }, Handler(Looper.getMainLooper()))
    }

    fun toggle(ctx: Context) {
        init(ctx)
        val id = camId ?: return
        runCatching { cm?.setTorchMode(id, !_on.value) }
    }
}
