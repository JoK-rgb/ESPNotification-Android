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
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.notificationlistener.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BluetoothService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val binder = LocalBinder()

    var isActive = false
        get() {
            return field
        }

    sealed class ConnectionState {
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "BluetoothServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
        private const val RX_CHAR_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
        private const val RECONNECTION_ATTEMPTS = 3
        private const val RECONNECTION_DELAY = 1000L // 1 second
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }

    fun startService() {
        isActive = true
        initializeBluetooth()
        updateServiceState(ConnectionState.Disconnected)
    }

    fun stopService() {
        isActive = false
        disconnect()
        updateServiceState(ConnectionState.Disconnected)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Bluetooth Service Running"), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, BluetoothService::class.java).also {
            it.setPackage(packageName)
        }
        startService(restartServiceIntent)
        super.onTaskRemoved(rootIntent)
    }

    private fun initializeBluetooth() {
        if (!hasBluetoothPermissions()) {
            updateServiceState(ConnectionState.Error("Missing Bluetooth permissions"))
            return
        }

        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter ?: run {
            updateServiceState(ConnectionState.Error("Bluetooth not available"))
            return
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Bluetooth Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Maintains Bluetooth connection"
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                updateServiceState(ConnectionState.Error("Failed to create notification channel"))
                stopSelf()
            }
        }
    }

    private fun createNotification(message: String): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Bluetooth Service")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        private var reconnectionAttempts = 0

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    updateServiceState(ConnectionState.Connected)
                    reconnectionAttempts = 0
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    updateServiceState(ConnectionState.Disconnected)
                    disconnectGatt()

                    if (reconnectionAttempts < RECONNECTION_ATTEMPTS) {
                        reconnectionAttempts++
                        serviceScope.launch {
                            kotlinx.coroutines.delay(RECONNECTION_DELAY)
                            reconnect()
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uartService = gatt.getService(UUID.fromString(SERVICE_UUID))
                rxCharacteristic = uartService?.getCharacteristic(UUID.fromString(RX_CHAR_UUID))
                rxCharacteristic?.let {
                    sendData("Service Connected".toByteArray())
                }
            } else {
                updateServiceState(ConnectionState.Error("Failed to discover services"))
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        if (!hasBluetoothPermissions()) {
            updateServiceState(ConnectionState.Error("Missing Bluetooth permissions"))
            return
        }

        try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)?.let { device ->
                bluetoothGatt = device.connectGatt(this, true, gattCallback)
            } ?: run {
                updateServiceState(ConnectionState.Error("Device not found"))
            }
        } catch (e: Exception) {
            updateServiceState(ConnectionState.Error("Failed to connect: ${e.message}"))
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconnect() {
        if (!hasBluetoothPermissions()) return

        bluetoothGatt?.connect() ?: run {
            updateServiceState(ConnectionState.Error("No device to reconnect to"))
        }
    }

    @SuppressLint("MissingPermission")
    fun sendData(data: ByteArray) {
        if (connectionState.value !is ConnectionState.Connected) return

        rxCharacteristic?.let { characteristic ->
            try {
                characteristic.value = data
                if (hasBluetoothPermissions()) {
                    bluetoothGatt?.writeCharacteristic(characteristic)
                }
            } catch (e: Exception) {
                updateServiceState(ConnectionState.Error("Failed to send data: ${e.message}"))
            }
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateServiceState(state: ConnectionState) {
        serviceScope.launch {
            _connectionState.emit(state)
            updateNotification(getNotificationMessage(state))
        }
    }

    private fun getNotificationMessage(state: ConnectionState): String = when (state) {
        is ConnectionState.Connected -> "Connected to device"
        is ConnectionState.Disconnected -> "Disconnected from device"
        is ConnectionState.Error -> "Error: ${state.message}"
    }

    private fun updateNotification(message: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, createNotification(message))
        } catch (e: Exception) {
            // Log error but don't update notification to avoid recursive calls
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectGatt() {
        if (hasBluetoothPermissions()) {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        }
        bluetoothGatt = null
        rxCharacteristic = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        disconnectGatt()
        stopForeground(true)
    }

    fun isConnected(): Boolean {
        return connectionState.value is ConnectionState.Connected
    }

    fun disconnect() {
        disconnectGatt()
        updateServiceState(ConnectionState.Disconnected)
    }
}