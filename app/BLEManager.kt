package com.example.test

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class BLEManager(private val context: Context, private val activity: Activity) {

    private val SERVICE_UUID = UUID.fromString("0000180C-0000-1000-8000-00805F9B34FB")
    private val DATA_UUID = UUID.fromString("00002A56-0000-1000-8000-00805F9B34FB")
    private val WRITE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805F9B34FB")

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    // 🔥 Shared data (accessible anywhere)
    private val _status = MutableStateFlow("Not connected")
    val status: StateFlow<String> = _status

    private val _index = MutableStateFlow(0f)
    val index: StateFlow<Float> = _index

    // ✅ GATT CALLBACK
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _status.value = "Connected!"
                bluetoothGatt = gatt

                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    gatt.discoverServices()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            val service = gatt.getService(SERVICE_UUID)

            val dataChar = service?.getCharacteristic(DATA_UUID)
            val writeChar = service?.getCharacteristic(WRITE_UUID)

            if (dataChar != null && writeChar != null) {

                writeCharacteristic = writeChar

                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

                    gatt.setCharacteristicNotification(dataChar, true)

                    val descriptor = dataChar.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )

                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }

                _status.value = "Ready!"
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value.toString(Charsets.UTF_8).trim()
            val parts = data.split(",")

            if (parts.size >= 6) {
                val index = parts[2].toFloatOrNull() ?: return
                _index.value = index
            }
        }
    }

    // ✅ SCAN CALLBACK
    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name

            if (name == "Tiny Ted's Telecom") {

                if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    scanner.stopScan(this)
                }

                _status.value = "Connecting..."

                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    bluetoothGatt = result.device.connectGatt(context, false, gattCallback)
                }
            }
        }
    }

    fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                1
            )
            return
        }

        _status.value = "Scanning..."
        scanner.startScan(scanCallback)
    }

    fun sendStart() {
        val char = writeCharacteristic ?: return

        char.value = "s".toByteArray()

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.writeCharacteristic(char)
            _status.value = "Streaming..."
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}