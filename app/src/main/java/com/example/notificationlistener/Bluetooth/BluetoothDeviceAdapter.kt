package com.example.notificationlistener.Bluetooth

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.notificationlistener.R
import java.util.concurrent.ConcurrentHashMap

// BluetoothDevice.kt
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val lastSeen: Long = System.currentTimeMillis()
)

class BluetoothDeviceAdapter (private val onDeviceClick: (BluetoothDeviceInfo) -> Unit) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {
    private val devices = ConcurrentHashMap<String, BluetoothDeviceInfo>()
    private val devicesList = mutableListOf<BluetoothDeviceInfo>()
    private val handler = Handler(Looper.getMainLooper())
    private val DEVICE_TIMEOUT = 3000L

    private val cleanupRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            var hasRemovals = false

            devices.entries.removeIf { entry ->
                val shouldRemove = currentTime - entry.value.lastSeen > DEVICE_TIMEOUT
                if (shouldRemove) hasRemovals = true
                shouldRemove
            }

            if (hasRemovals) {
                updateDevicesList()
            }

            handler.postDelayed(this, 2000) // Run cleanup every 2 seconds
        }
    }

    init {
        handler.post(cleanupRunnable)
    }

    inner class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceAddress: TextView = itemView.findViewById(R.id.deviceAddress)

        init {
            itemView.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDeviceClick(devicesList[position])
                }
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devicesList[position]
        holder.deviceName.text = device.name
        holder.deviceAddress.text = device.address
    }

    override fun getItemCount() = devices.size

    fun addDevice(device: BluetoothDeviceInfo) {
        devices[device.address] = device
        updateDevicesList()
    }

    private fun updateDevicesList() {
        devicesList.clear()
        devicesList.addAll(devices.values.sortedByDescending { it.address })
        notifyDataSetChanged()
    }

    fun clearDevices() {
        devices.clear()
        notifyDataSetChanged()
    }
}
