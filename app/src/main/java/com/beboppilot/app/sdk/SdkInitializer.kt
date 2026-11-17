package com.beboppilot.app.sdk

import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Build
import com.parrot.arsdk.ARSDK

sealed interface NativeLoadStatus {
    data object Pending : NativeLoadStatus
    data class Loaded(val message: String) : NativeLoadStatus
    data class Failed(val error: String) : NativeLoadStatus
}

data class UsbAccessorySummary(
    val manufacturer: String?,
    val model: String?,
    val version: String?,
    val serial: String?,
    val hasPermission: Boolean,
)

data class SdkInitUiState(
    val nativeLoadStatus: NativeLoadStatus = NativeLoadStatus.Pending,
    val androidVersion: String = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
    val usbAccessories: List<UsbAccessorySummary> = emptyList(),
)

object SdkInitializer {
    fun loadNativeLibraries(): NativeLoadStatus {
        return try {
            val loaded = ARSDK.loadSDKLibs()
            if (loaded) {
                NativeLoadStatus.Loaded("ARSDK.loadSDKLibs() completado correctamente")
            } else {
                NativeLoadStatus.Failed("ARSDK.loadSDKLibs() devolvió false")
            }
        } catch (t: Throwable) {
            NativeLoadStatus.Failed("${t::class.java.simpleName}: ${t.message ?: "sin detalle"}")
        }
    }

    fun inspectUsbAccessories(context: Context): List<UsbAccessorySummary> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return emptyList()
        val list = usbManager.accessoryList ?: return emptyList()
        return list.map { accessory ->
            UsbAccessorySummary(
                manufacturer = accessory.manufacturer,
                model = accessory.model,
                version = accessory.version,
                serial = accessory.serial,
                hasPermission = usbManager.hasPermission(accessory),
            )
        }
    }
}
