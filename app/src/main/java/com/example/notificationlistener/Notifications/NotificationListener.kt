package com.example.notificationlistener.Notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.notificationlistener.Bluetooth.BluetoothService

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
        // Bind to the BluetoothService
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

            // Extract notification content
            val extras = sbn.notification.extras
            val title = extras?.getCharSequence("android.title")?.toString() ?: "No Title"
            val text = extras?.getCharSequence("android.text")?.toString() ?: "No Text"

            sendData("$appName: $title - $text")
        } catch (e: Exception) {
            // Handle any exceptions that might occur during notification processing
        }
    }

    private fun sendData(data: String) {
        if (bound && bluetoothService != null) {
            bluetoothService?.sendData(data)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bound) {
            unbindService(connection)
            bound = false
        }
    }
}
