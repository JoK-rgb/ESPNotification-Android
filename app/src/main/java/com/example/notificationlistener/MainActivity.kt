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
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_START_SERVICE
import com.example.notificationlistener.Bluetooth.BluetoothConstants.Companion.BL_STOP_SERVICE
import com.example.notificationlistener.Bluetooth.BluetoothDeviceAdapter
import com.example.notificationlistener.Bluetooth.BluetoothDeviceInfo
import com.example.notificationlistener.Bluetooth.BluetoothService
import com.example.notificationlistener.Bluetooth.BluetoothService.ConnectionState
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

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as BluetoothService.LocalBinder
            bluetoothService = binder.getService()
            bound = true

            observeConnectionState()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bluetoothService = null
            bound = false
            updateUIState(ConnectionState.Disconnected)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupBluetoothService()
        setupBluetoothScanning()
        setupClickListeners()
    }

    private fun initializeViews() {
        connectDeviceButton = findViewById(R.id.scanBluetoothButton)
        toggleServiceButton = findViewById(R.id.toggleServiceButton)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        connectionStatusText.text = "Disconnected"
        connectDeviceButton.isEnabled = false
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
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start Bluetooth service", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeConnectionState() {
        lifecycleScope.launch {
            bluetoothService?.connectionState?.collect { state ->
                updateUIState(state)
            }
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
        findViewById<Button>(R.id.allowPermissionsButton).setOnClickListener {
            checkAndRequestNotificationPermission()
        }

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

    private fun handleServiceToggle() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (bound) {
                showProgressDialog("Processing...")
                withContext(Dispatchers.IO) {
                    if (bluetoothService?.isConnected() == true) {
                        bluetoothService?.stopService()
                    } else {
                        bluetoothService?.startService()
                    }
                }
                updateUIForServiceState(bluetoothService?.isActive == true)
                dismissProgressDialog()
            }
        }
    }

    private fun updateUIForServiceState(isRunning: Boolean) {
        updateServiceButtonState(isRunning)
        connectDeviceButton.isEnabled = isRunning && !bluetoothService?.isConnected()!!
    }

    private fun updateServiceButtonState(isRunning: Boolean) {
        toggleServiceButton.text = if (isRunning) BL_STOP_SERVICE else BL_START_SERVICE
        toggleServiceButton.backgroundTintList = ColorStateList.valueOf(
            if (isRunning) Color.RED else Color.GREEN
        )
    }

    private fun updateUIState(state: ConnectionState) {
        runOnUiThread {
            when (state) {
                is ConnectionState.Connected -> {
                    connectDeviceButton.isEnabled = false
                    connectionStatusText.text = "Connected"
                    updateServiceButtonState(true)
                    Toast.makeText(this, "Connected to device", Toast.LENGTH_SHORT).show()
                }
                is ConnectionState.Disconnected -> {
                    // Only enable connect button if service is running
                    connectDeviceButton.isEnabled = bluetoothService?.isActive == true
                    connectionStatusText.text = "Disconnected"
                    updateServiceButtonState(bluetoothService?.isActive == true)
                }
                is ConnectionState.Error -> {
                    connectDeviceButton.isEnabled = bluetoothService?.isActive == true
                    connectionStatusText.text = "Error"
                    updateServiceButtonState(bluetoothService?.isActive == true)
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        runOnUiThread {
            progressDialog?.dismiss()
            progressDialog = AlertDialog.Builder(this)
                .setMessage(message)
                .setCancelable(false)
                .create()
            progressDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            progressDialog?.show()
        }
    }

    private fun dismissProgressDialog() {
        runOnUiThread {
            progressDialog?.dismiss()
            progressDialog = null
        }
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