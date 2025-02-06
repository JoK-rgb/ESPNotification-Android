package com.example.notificationlistener

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.example.notificationlistener.Bluetooth.BluetoothDeviceInfo
import java.util.UUID

class BluetoothConnection(
    private val activity: ComponentActivity,
    private val onDeviceFound: (BluetoothDeviceInfo) -> Unit
) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    var isScanning: Boolean = false
        private set

    companion object {
        var instance: BluetoothConnection? = null
    }

    init {
        instance = this
    }

    private val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            startBluetoothScan()
        } else {
            Toast.makeText(
                activity,
                "Permissions required for Bluetooth scanning",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            try {
                if (ContextCompat.checkSelfPermission(
                        activity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val deviceName = result.device.name
                    val deviceAddress = result.device.address
                    if (result.isConnectable && !deviceName.isNullOrEmpty()) {
                        activity.runOnUiThread {
                            onDeviceFound(
                                BluetoothDeviceInfo(
                                    name = deviceName,
                                    address = deviceAddress,
                                    lastSeen = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "Bluetooth connect permission not granted",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: SecurityException) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Security exception: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun checkBluetoothPermissions() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            try {
                activity.startActivity(enableBtIntent)
            } catch (e: SecurityException) {
                Toast.makeText(activity, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            startBluetoothScan()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    fun startBluetoothScan() {
        if (isScanning) {
            stopBluetoothScan()
            return
        }

        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            Toast.makeText(activity, "Missing required permissions", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            isScanning = true
            bluetoothAdapter.bluetoothLeScanner?.startScan(scanCallback)
        } catch (e: SecurityException) {
            isScanning = false
            Toast.makeText(
                activity,
                "Failed to start scanning: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun stopBluetoothScan() {
        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) return

        try {
            isScanning = false
            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Toast.makeText(
                activity,
                "Failed to stop scanning: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Connected to device", Toast.LENGTH_SHORT).show()
                }
                // Discover services when connected.
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt?.discoverServices()
                } else {
                    Toast.makeText(activity, "Bluetooth connect permission not granted", Toast.LENGTH_SHORT).show()
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Disconnected from device", Toast.LENGTH_SHORT).show()
                }
                bluetoothGatt = null
                rxCharacteristic = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val uartService = gatt.getService(UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
                if (uartService != null) {
                    rxCharacteristic = uartService.getCharacteristic(UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"))
                    rxCharacteristic?.let {
                        sendData("Connected to App")
                    }
                } else {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "UART service not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun connectToDevice(deviceAddress: String) {
        val device: BluetoothDevice? = bluetoothAdapter.getRemoteDevice(deviceAddress)
        if (device == null) {
            Toast.makeText(activity, "Device not found", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothGatt = device.connectGatt(activity, false, gattCallback)
            stopBluetoothScan()
        } else {
            Toast.makeText(activity, "Bluetooth connect permission not granted", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendData(data: String) {
        rxCharacteristic?.let { characteristic ->
            characteristic.value = data.toByteArray()
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothGatt?.writeCharacteristic(characteristic)
            } else {
                Toast.makeText(activity, "Bluetooth connect permission not granted", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(activity, "No RX characteristic available", Toast.LENGTH_SHORT).show()
        }
    }
}
