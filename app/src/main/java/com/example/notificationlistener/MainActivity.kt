package com.example.notificationlistener

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlistener.Bluetooth.BluetoothDeviceAdapter
import com.example.notificationlistener.Bluetooth.BluetoothDeviceInfo

class MainActivity : ComponentActivity() {

    private lateinit var bluetoothConnection: BluetoothConnection
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private var deviceDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the BluetoothConnection. Discovered devices are forwarded to the adapter.
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
                // Use the BluetoothConnection to handle the connection.
                bluetoothConnection.connectToDevice(device.address)
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
        deviceDialog?.dismiss()
        deviceDialog = null
    }
}
