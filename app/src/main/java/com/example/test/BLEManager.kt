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

data class FingerMaxData(
    val timestamp: Long,
    val thumb: Float,
    val index: Float,
    val middle: Float,
    val ring: Float,
    val pinky: Float
)

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


    private val _thumb = MutableStateFlow(0f)
    val thumb: StateFlow<Float> = _thumb

    private val _index = MutableStateFlow(0f)
    val index: StateFlow<Float> = _index

    private val _middle = MutableStateFlow(0f)
    val middle: StateFlow<Float> = _middle

    private val _ring = MutableStateFlow(0f)
    val ring: StateFlow<Float> = _ring

    private val _pinky = MutableStateFlow(0f)
    val pinky: StateFlow<Float> = _pinky

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

        fun saveMaxData(context: Context, data: FingerMaxData) {

            val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

            val existing = prefs.getString("history", "") ?: ""

            val newEntry = listOf(
                data.timestamp,
                data.thumb,
                data.index,
                data.middle,
                data.ring,
                data.pinky
            ).joinToString(",")

            val updated = if (existing.isEmpty()) newEntry else "$existing;$newEntry"

            prefs.edit().putString("history", updated).apply()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value.toString(Charsets.UTF_8).trim()
            val receivedString = String(characteristic.value)

            val parts = data.split(",")

            if (receivedString.startsWith("MAX")) {

                val parts = receivedString.split(",")

                if (parts.size == 6) {
                    val data = FingerMaxData(
                        timestamp = System.currentTimeMillis(),
                        thumb = parts[1].toFloat(),
                        index = parts[2].toFloat(),
                        middle = parts[3].toFloat(),
                        ring = parts[4].toFloat(),
                        pinky = parts[5].toFloat()
                    )

                    saveMaxData(context, data)
                }
            }

            if (parts.size >= 6) {
                _thumb.value = parts[1].toFloatOrNull() ?: return
                _index.value = parts[2].toFloatOrNull() ?: return
                _middle.value = parts[3].toFloatOrNull() ?: return
                _ring.value = parts[4].toFloatOrNull() ?: return
                _pinky.value = parts[5].toFloatOrNull() ?: return
            }


        }
    }
    fun loadMaxData(context: Context): List<FingerMaxData> {

        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val raw = prefs.getString("history", "") ?: ""

        if (raw.isEmpty()) return emptyList()

        return raw.split(";").mapNotNull { entry ->
            val parts = entry.split(",")

            if (parts.size == 6) {
                FingerMaxData(
                    timestamp = parts[0].toLong(),
                    thumb = parts[1].toFloat(),
                    index = parts[2].toFloat(),
                    middle = parts[3].toFloat(),
                    ring = parts[4].toFloat(),
                    pinky = parts[5].toFloat()
                )
            } else null
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

    fun sendEnd() {
        val char = writeCharacteristic ?: return

        char.value = "e".toByteArray()

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    fun sendCalibrate() {
        val char = writeCharacteristic ?: return

        char.value = "c".toByteArray()

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun clearProgress(context: Context) {
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}