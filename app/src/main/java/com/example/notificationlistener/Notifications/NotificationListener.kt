package com.example.notificationlistener.Notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.notificationlistener.Bluetooth.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationListener : NotificationListenerService() {
    private var componentName: ComponentName? = null
    private var bluetoothService: BluetoothService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bluetoothService = null
            bound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Intent(this, BluetoothService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if(componentName == null) {
            componentName = ComponentName(this, this::class.java)
        }

        componentName?.let {
            requestRebind(it)
            toggleNotificationListenerService(it)
        }
        return START_STICKY
    }

    private fun toggleNotificationListenerService(componentName: ComponentName) {
        val pm = packageManager
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        if (componentName == null) {
            componentName = ComponentName(this, this::class.java)
        }

        componentName?.let { requestRebind(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            val packageName = sbn?.packageName ?: return
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(applicationInfo).toString()

            val extras = sbn.notification.extras
            val title = extras?.getCharSequence("android.title")?.toString() ?: "No Title"
            val text = extras?.getCharSequence("android.text")?.toString() ?: "No Text"

            sendData("$appName||$title||$text")
        } catch (e: Exception) {
            Log.e("NotificationListener", "Error processing notification: ${e.message}")
        }
    }

    private fun sendData(data: String) {
        if (bound && bluetoothService != null) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val byteArray = trimToCharacterAt(replaceUmlauts(data), 150).toByteArray(Charsets.ISO_8859_1)
                    bluetoothService?.sendData(byteArray)
                } catch (e: Exception) {
                    Log.e("NotificationListener", "Error sending data: ${e.message}")
                }
            }
        } else {
            Log.w("NotificationListener", "BluetoothService not bound or not available.")
        }
    }

    private fun replaceUmlauts(data: String) : String {
        return data
            .replace("Ä", "\u008E")  // CP437 for Ä (0x8E)
            .replace("Ö", "\u0099")  // CP437 for Ö (0x99)
            .replace("Ü", "\u009A")  // CP437 for Ü (0x9A)
            .replace("ä", "\u0084")  // CP437 for ä (0x84)
            .replace("ö", "\u0094")  // CP437 for ö (0x94)
            .replace("ü", "\u0081")  // CP437 for ü (0x81)
            .replace("ß", "\u00E1")  // CP437 for ß (0xE1)
    }

    private fun trimToCharacterAt(message: String, characterNumber: Int) : String {
        return message.take(characterNumber)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
