package com.nostr.tvframe.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.util.Log
import com.nostr.tvframe.TVFrameApp
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver listening for sudden USB mass-storage removal (`MEDIA_EJECT`,
 * `MEDIA_UNMOUNTED`, `MEDIA_BAD_REMOVAL`, `USB_DEVICE_DETACHED`) while the app or
 * DreamService screensaver is running. Immediately triggers fallback to internal TV
 * storage and removes stale non-favorited USB entries without crashing.
 */
class UsbHotPlugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val dataPath = intent.data?.path
        val app = TVFrameApp.instance

        when (action) {
            Intent.ACTION_MEDIA_EJECT,
            Intent.ACTION_MEDIA_UNMOUNTED,
            Intent.ACTION_MEDIA_BAD_REMOVAL,
            Intent.ACTION_MEDIA_REMOVED,
            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                Log.w(TAG, "USB storage detached ($action at $dataPath). Falling back to internal storage.")
                app.usbStorageDataSource.onUsbUnpluggedEvent(dataPath)
                app.applicationScope.launch {
                    app.database.photoDao().removeTemporaryUsbEntries()
                }
            }

            Intent.ACTION_MEDIA_MOUNTED,
            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Log.i(TAG, "USB storage attached ($action at $dataPath).")
                app.usbStorageDataSource.onUsbMountedEvent(dataPath)
                app.applicationScope.launch {
                    app.usbStorageDataSource.verifyUsbMountOrFallback()
                }
            }
        }
    }

    companion object {
        private const val TAG = "UsbHotPlugReceiver"
    }
}
