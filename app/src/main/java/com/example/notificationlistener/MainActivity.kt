package com.example.notificationlistener

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_CONNECTED
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_CONNECTED_TO_DEVICE
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_DISCONNECTED
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_START_SERVICE
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_STOP_SERVICE
import com.example.notificationlistener.Bluetooth.BluetoothDeviceAdapter
import com.example.notificationlistener.Bluetooth.BluetoothDeviceInfo
import com.example.notificationlistener.Bluetooth.BluetoothService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothConnection: BluetoothScan
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var connectDeviceButton: Button
    private lateinit var toggleServiceButton: Button
    private lateinit var connectionStatusText: TextView

    private var deviceDialog: AlertDialog? = null
    private var progressDialog: AlertDialog? = null
    private var bluetoothService: BluetoothService? = null
    private var bound = false

    private val connectionStateListener = object : BluetoothService.ConnectionStateListener {
        override fun onConnectionStateChanged(isConnected: Boolean) {
            runOnUiThread {
                updateConnectionStatus(isConnected)
            }
        }

        override fun onServiceStateChanged(isRunning: Boolean) {
            runOnUiThread {
                updateServiceStatus(isRunning)
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            bound = true
            bluetoothService?.connectionStateListener = connectionStateListener
            updateServiceStatus(bluetoothService?.isRunning == true)
            updateConnectionStatus(bluetoothService?.isConnected == true)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bluetoothService = null
            bound = false
            updateServiceStatus(false)
            updateConnectionStatus(false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!isNotificationPermissionGranted()) {
            checkAndRequestNotificationPermission()
        }

        initializeViews()
        setupBluetoothService()
        setupBluetoothScanning()
        setupClickListeners()

        connectionStatusText = findViewById(R.id.connectionStatusText)
        connectionStatusText.text = BL_DISCONNECTED
    }

    private fun initializeViews() {
        connectDeviceButton = findViewById(R.id.scanBluetoothButton)
        toggleServiceButton = findViewById(R.id.toggleServiceButton)
    }

    private fun setupBluetoothService() {
        try {
            Intent(this, BluetoothService::class.java).also { intent ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
                bluetoothService?.requestBatteryOptimization()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start Bluetooth service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupBluetoothScanning() {
        bluetoothConnection = BluetoothScan(this) { device ->
            if (::deviceAdapter.isInitialized) {
                deviceAdapter.addDevice(device)
            }
        }
    }

    private fun setupClickListeners() {
        connectDeviceButton.setOnClickListener {
            if (!bluetoothConnection.isScanning) {
                showDeviceDialog()
            }
            bluetoothConnection.checkBluetoothPermissions()
        }

        toggleServiceButton.setOnClickListener {
            handleServiceToggle()
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabledListeners.contains(packageName)
    }



    private fun handleServiceToggle() {
        if (isBluetoothServiceRunning()) {
            lifecycleScope.launch(Dispatchers.Main) {
                stopBluetoothService()
            }
        } else {
            startBluetoothService()
        }
    }

    private suspend fun stopBluetoothService() {
        bluetoothService?.let { service ->
            if (service.isConnected) {
                showProgressDialog("Disconnecting device...")
                withContext(Dispatchers.IO) {
                    service.stopService()
                }
                dismissProgressDialog()
            } else {
                service.stopService()
            }

            BluetoothScan.instance?.stopBluetoothScan()
            updateServiceStatus(false)
        }
    }

    private fun startBluetoothService() {
        bluetoothService?.startService()
        updateServiceStatus(true)
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        connectDeviceButton.isEnabled = !isConnected && bluetoothService?.isRunning == true
        connectionStatusText.text = if(!isConnected && bluetoothService?.isRunning == true)
            BL_CONNECTED else BL_DISCONNECTED

        val message = if (isConnected) BL_CONNECTED_TO_DEVICE else BL_DISCONNECTED
        connectionStatusText.text = if(isConnected) BL_CONNECTED else BL_DISCONNECTED
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateServiceStatus(isRunning: Boolean) {
        connectDeviceButton.isEnabled = isRunning
        toggleServiceButton.text = if (isRunning) BL_STOP_SERVICE else BL_START_SERVICE
        toggleServiceButton.backgroundTintList = ColorStateList.valueOf(
            if (isRunning) Color.RED else Color.GREEN
        )
    }

    private fun isBluetoothServiceRunning(): Boolean {
        return bluetoothService?.isRunning == true
    }

    private fun checkAndRequestNotificationPermission() {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).also {
            startActivity(it)
        }
    }

    private fun showDeviceDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_bluetooth_devices, null)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.devicesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

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

    private fun showConnectionDialog(device: BluetoothDeviceInfo) {
        AlertDialog.Builder(this)
            .setTitle("Connect to Device")
            .setMessage("Would you like to connect to ${device.name}?")
            .setPositiveButton("Yes") { dialog, _ ->
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

    private fun showProgressDialog(message: String) {
        progressDialog?.dismiss()
        progressDialog = AlertDialog.Builder(this)
            .setMessage(message)
            .setCancelable(false)
            .create()
        progressDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        progressDialog?.show()
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        if (bluetoothConnection.isScanning) {
            bluetoothConnection.stopBluetoothScan()
        }
        if (bound) {
            unbindService(connection)
            bound = false
        }
        deviceDialog?.dismiss()
        deviceDialog = null
        progressDialog?.dismiss()
        progressDialog = null
    }
}

