package com.example.notificationlistener

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
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

class BluetoothConnection(
    private val activity: ComponentActivity,
    /**
     * Called whenever a Bluetooth LE device is discovered.
     * In your activity you can forward the discovered device to your adapter.
     */
    private val onDeviceFound: (BluetoothDeviceInfo) -> Unit
) {

    // Initialize Bluetooth adapter using the system's BluetoothManager.
    private val bluetoothManager =
        activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    var isScanning: Boolean = false
        private set

    // Define required permissions based on the API level.
    private val requiredPermissions: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    // Register for the permission request callback.
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

    // Scan callback: called for each discovered Bluetooth LE device.
    private val scanCallback = object : ScanCallback() {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            try {
                val deviceName = result.device.name
                val deviceAddress = result.device.address
                // Only add connectable devices with a non-empty name.
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
            } catch (e: SecurityException) {
                activity.runOnUiThread {
                    Toast.makeText(
                        activity,
                        "Cannot access device information: permission denied",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Checks whether Bluetooth is enabled and whether the required permissions have been granted.
     * If permissions are missing, the system permission dialog will be shown.
     * If Bluetooth is disabled, an intent to enable it is fired.
     */
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

    /**
     * Starts scanning for Bluetooth LE devices.
     * If a scan is already in progress, it stops the scan.
     */
    fun startBluetoothScan() {
        // If already scanning, stop scanning.
        if (isScanning) {
            stopBluetoothScan()
            return
        }

        // Check for required permissions.
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

    /**
     * Stops the Bluetooth LE scan.
     */
    fun stopBluetoothScan() {
        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (!hasPermissions) {
            return
        }

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
}
