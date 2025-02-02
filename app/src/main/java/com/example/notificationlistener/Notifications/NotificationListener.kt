package com.example.notificationlistener.Notifications

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.notificationlistener.BluetoothConnection

class NotificationListener : NotificationListenerService() {
    private var componentName: ComponentName? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        if(componentName == null) {
            componentName = ComponentName(this, this::class.java)
        }

        componentName?.let {
            requestRebind(it)
            toggleNotificationListenerService(it)
        }
        return START_REDELIVER_INTENT
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
        val packageName = sbn?.packageName ?: return // Get the package name

        // Use PackageManager to get the app name
        val packageManager = applicationContext.packageManager
        val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
        val appName = packageManager.getApplicationLabel(applicationInfo).toString()

        // Extract notification content
        val extras = sbn.notification.extras
        val title = extras?.getCharSequence("android.title")?.toString() ?: "No Title"
        val text = extras?.getCharSequence("android.text")?.toString() ?: "No Text"

        Log.d("NotificationListener", "Notification from $appName: $title - $text")

        sendData("$appName: $title - $text")
    }

    /**
     * Sends the given data to the ESP32 via the shared BluetoothConnection.
     */
    private fun sendData(data: String) {
        // Ensure that our shared BluetoothConnection instance is available.
        val bluetoothConnection = BluetoothConnection.instance
        if (bluetoothConnection != null) {
            bluetoothConnection.sendData(data)
            Log.i("NotificationListener", "Sent data: $data")
        } else {
            Log.e("NotificationListener", "Bluetooth connection is not available")
        }
    }
}