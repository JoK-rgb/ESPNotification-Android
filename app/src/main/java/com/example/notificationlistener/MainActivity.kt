package com.example.notificationlistener

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlistener.Bluetooth.BluetoothDeviceAdapter
import com.example.notificationlistener.Bluetooth.BluetoothDeviceInfo
import com.example.notificationlistener.Bluetooth.BluetoothService

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothConnection: BluetoothConnection
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private var deviceDialog: AlertDialog? = null
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start and bind to the Bluetooth service
        try {
            Intent(this, BluetoothService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        } catch (e: Exception) {
            // Handle service start failure
            Toast.makeText(this, "Failed to start Bluetooth service", Toast.LENGTH_SHORT).show()
        }

        // Initialize the BluetoothConnection
        bluetoothConnection = BluetoothConnection(this) { device ->
            if (::deviceAdapter.isInitialized) {
                deviceAdapter.addDevice(device)
            }
        }

        // Button to allow notification listener permissions.
        findViewById<Button>(R.id.allowPermissionsButton).setOnClickListener {
            checkAndRequestNotificationPermission()
        }

        // Button to start scanning for Bluetooth devices.
        findViewById<Button>(R.id.scanBluetoothButton).setOnClickListener {
            if (!bluetoothConnection.isScanning) {
                showDeviceDialog()
            }
            bluetoothConnection.checkBluetoothPermissions()
        }
    }

    /**
     * Redirects to the notification listener settings.
     */
    private fun checkAndRequestNotificationPermission() {
        redirectToSettings()
    }

    private fun redirectToSettings() {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).also {
            startActivity(it)
        }
    }

    /**
     * Displays a dialog with a list of discovered Bluetooth devices.
     */
    private fun showDeviceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bluetooth_devices, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize the adapter. The callback is used when a device is selected.
        deviceAdapter = BluetoothDeviceAdapter { device ->
            showConnectionDialog(device)
        }

        recyclerView.adapter = deviceAdapter

        deviceDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.closeButton).setOnClickListener {
            bluetoothConnection.stopBluetoothScan()
            deviceDialog?.dismiss()
            deviceDialog = null
        }

        deviceAdapter.clearDevices()
        deviceDialog?.show()
    }

    /**
     * Shows a confirmation dialog asking if the user wants to connect to the selected device.
     */
    private fun showConnectionDialog(device: BluetoothDeviceInfo) {
        AlertDialog.Builder(this)
            .setTitle("Connect to Device")
            .setMessage("Would you like to connect to ${device.name}?")
            .setPositiveButton("Yes") { dialog, _ ->
                // Use the BluetoothService instead of BluetoothConnection
                bluetoothService?.connectToDevice(device.address)
                dialog.dismiss()
                bluetoothConnection.stopBluetoothScan()
                deviceDialog?.dismiss()
                deviceDialog = null
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (bluetoothConnection.isScanning) {
            bluetoothConnection.stopBluetoothScan()
        }
        if (bound) {
            unbindService(connection)
            bound = false
        }
        deviceDialog?.dismiss()
        deviceDialog = null
    }
}

