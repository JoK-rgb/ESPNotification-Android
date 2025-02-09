package com.example.notificationlistener.Bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.notificationlistener.MainActivity
import java.util.UUID

class BluetoothService : Service() {

    interface ConnectionStateListener {
        fun onConnectionStateChanged(isConnected: Boolean)
        fun onServiceStateChanged(isRunning: Boolean)
    }

    var connectionStateListener: ConnectionStateListener? = null
        set(value) {
            field = value
        }

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val binder = LocalBinder()

    var isConnected = false
    var isRunning = false

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
        private const val RX_CHAR_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    override fun onCreate() {
        super.onCreate()
        startService()
    }

    fun startService() {
        initializeBluetooth()
        startForegroundService()
        isRunning = true;
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, BluetoothService::class.java).also {
            it.setPackage(packageName)
        }
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    fun requestBatteryOptimization() {
        val intent = Intent().apply {
            action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun initializeBluetooth() {
        try {
            bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null) {
                stopSelf()
                return
            }

            createNotificationChannel()
            startForegroundService()
        } catch (e: Exception) {
            stopSelf()
            updateNotification("Error initializing Bluetooth")
        }
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            NotificationCompat.Builder(this)
        }

        return builder
            .setContentTitle("Bluetooth Service")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID,"Bluetooth Service",NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Maintains Bluetooth connection"
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                updateNotification("Notification creation failure")
                stopSelf()
            }
        }
    }

    @SuppressLint("ForegroundServiceType")
    private fun startForegroundService() {
        try {
            startForeground(NOTIFICATION_ID, createNotification("Bluetooth Service Running"))
        } catch (e: Exception) {
            stopSelf()
            updateNotification("Error running Bluetooth service")
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        try {
            startForeground(1, createNotification("Bluetooth is running in the background"))
        } catch (e: Exception) {
            updateNotification("Error starting Bluetooth service")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    updateNotification("Connected to device")
                    connectionStateListener?.onConnectionStateChanged(true)
                    checkPermissions()
                    bluetoothGatt?.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    updateNotification("Disconnected from device")
                    connectionStateListener?.onConnectionStateChanged(false)

                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    rxCharacteristic = null

                    if (isRunning) {
                        reconnect()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uartService = gatt.getService(UUID.fromString(SERVICE_UUID))
                if (uartService != null) {
                    rxCharacteristic = uartService.getCharacteristic(UUID.fromString(RX_CHAR_UUID))
                    rxCharacteristic?.let {
                        sendData("Service Connected".toByteArray())
                    }
                }
            }
        }
    }

    private fun updateNotification(message: String) {
        try {
            val notification = createNotification(message)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            updateNotification("Notification update failure")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        checkPermissions()

        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            device?.let {
                bluetoothGatt = it.connectGatt(this, true, gattCallback)
            }
        } catch (e: Exception) {
            updateNotification("Failed to connect to device")
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconnect() {
        bluetoothGatt?.let { gatt ->
            checkPermissions()

           try {
                gatt.connect()
            } catch (e: Exception) {
                updateNotification("Reconnection error")
            }
        }
    }

    fun sendData(data: ByteArray) {
        if (!isConnected) return

        rxCharacteristic?.let { characteristic ->
            try {
                characteristic.value = data
                if (ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt?.writeCharacteristic(characteristic)
                }
            } catch (e: Exception) {
                updateNotification("Error sending data")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
    }

    @SuppressLint("MissingPermission")
    fun stopService() {
        try {
            checkPermissions()

            bluetoothGatt?.let { gatt ->
                gatt.disconnect()
                gatt.close()
                bluetoothGatt = null
            }

            rxCharacteristic = null
            isConnected = false
            isRunning = false

            connectionStateListener?.onConnectionStateChanged(false)
            connectionStateListener?.onServiceStateChanged(false)

            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            updateNotification("Error stopping service")
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Missing Bluetooth permissions")
        }
    }
}