package com.example.test //link to app

//import all packages
import android.Manifest //android permissions
import android.app.Activity //interact with current screen open on app
import android.bluetooth.*  //BLE classes
import android.bluetooth.le.* //BLE classes
import android.content.Context //system services e.g Bluetooth/storage
import android.content.pm.PackageManager //check permissions granted
import androidx.core.app.ActivityCompat //check permissions granted
import kotlinx.coroutines.flow.MutableStateFlow //changes state
import kotlinx.coroutines.flow.StateFlow //read only
import java.util.UUID //used to identify UUIDs

data class FingerMaxData( //structure to store finger data
    val timestamp: Long,
    val thumb: Float,
    val index: Float,
    val middle: Float,
    val ring: Float,
    val pinky: Float
)

class BLEManager(private val context: Context, private val activity: Activity) { //context for system access and activity for permissions
    //matching UUIDs as arduino
    private val SERVICE_UUID = UUID.fromString("0000180C-0000-1000-8000-00805F9B34FB")
    private val DATA_UUID = UUID.fromString("00002A56-0000-1000-8000-00805F9B34FB")
    private val WRITE_UUID = UUID.fromString("0000abcd-0000-1000-8000-00805F9B34FB")

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val bluetoothAdapter = bluetoothManager.adapter
    private val scanner = bluetoothAdapter.bluetoothLeScanner //scans for BLE devices

    private var bluetoothGatt: BluetoothGatt? = null  //represents active BLE connection
    private var writeCharacteristic: BluetoothGattCharacteristic? = null //sends commands to connected device

    //_name means that the value is changeable
    //name means that the value is read only
    private val _status = MutableStateFlow("Not connected") //device always initially disconnected when app opens
    val status: StateFlow<String> = _status //causes value to automatically update in user interface
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

    private val gattCallback = object : BluetoothGattCallback() { //handles BLE events e.g connect/read/write

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when (newState) {

                BluetoothProfile.STATE_CONNECTED -> {  //if connected update status
                    _status.value = "Connected!"
                    bluetoothGatt = gatt //save connection

                    if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) { //ask device for services e.g sensor etc
                        gatt.discoverServices()
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _status.value = "Disconnected"  //if disconnected update status

                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    writeCharacteristic = null //remove any saved characteristics
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            val service = gatt.getService(SERVICE_UUID) //find BLE service

            val dataChar = service?.getCharacteristic(DATA_UUID) //find BLE characteristics
            val writeChar = service?.getCharacteristic(WRITE_UUID)

            if (dataChar != null && writeChar != null) { //if found both characteristics

                writeCharacteristic = writeChar

                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {

                    gatt.setCharacteristicNotification(dataChar, true) //enable notifications from connected device

                    val descriptor = dataChar.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                    )

                    descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE //enable notifications
                    gatt.writeDescriptor(descriptor)
                }

                _status.value = "Ready!" //set status to ready once connected and enabled notifications
            }
        }

        fun saveMaxData(context: Context, data: FingerMaxData) {

            val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE) //shared preferences refers to local saved storage within app memory

            val existing = prefs.getString("history", "") ?: "" //get existing data

            val newEntry = listOf( //convert data to CSV format
                data.timestamp,
                data.thumb,
                data.index,
                data.middle,
                data.ring,
                data.pinky
            ).joinToString(",")

            val updated = if (existing.isEmpty()) newEntry else "$existing;$newEntry" //add new data onto old data

            prefs.edit().putString("history", updated).apply() //save new data
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value.toString(Charsets.UTF_8).trim() //convert bytes to strings
            val receivedString = String(characteristic.value)

            val parts = data.split(",")  //use commas as delimiters

            if (receivedString.startsWith("MAX")) { //check string starts with 'MAX'

                val parts = receivedString.split(",") //split again

                if (parts.size == 6) { //check correct number of entries
                    val data = FingerMaxData( //create object
                        timestamp = System.currentTimeMillis(), //save when this max was created
                        thumb = parts[1].toFloat(), //extract parameters based on index in string
                        index = parts[2].toFloat(),
                        middle = parts[3].toFloat(),
                        ring = parts[4].toFloat(),
                        pinky = parts[5].toFloat()
                    )

                    saveMaxData(context, data) //store it
                }
            }

            if (parts.size >= 6) { //update values for each finger in UI
                _thumb.value = parts[1].toFloatOrNull() ?: return
                _index.value = parts[2].toFloatOrNull() ?: return
                _middle.value = parts[3].toFloatOrNull() ?: return
                _ring.value = parts[4].toFloatOrNull() ?: return
                _pinky.value = parts[5].toFloatOrNull() ?: return
            }


        }
    }
    fun loadMaxData(context: Context): List<FingerMaxData> { //open CSVs

        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        val raw = prefs.getString("history", "") ?: ""

        if (raw.isEmpty()) return emptyList()

        return raw.split(";").mapNotNull { entry -> //use semicolon to split different entries
            val parts = entry.split(",") //use commas to split values

            if (parts.size == 6) {
                FingerMaxData( //extract parameters based on index in string
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

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name //get device name

            if (name == "Tiny Ted's Telecom") { //only connect if name is Tiny Ted's telecom

                if (hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                    scanner.stopScan(this)  //stop scanning for devices
                }

                _status.value = "Connecting..." //update status

                if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                    bluetoothGatt = result.device.connectGatt(context, false, gattCallback) //connect to device
                }
            }
        }
    }

    fun startScan() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) { //check permissions
            ActivityCompat.requestPermissions( //ask user for permissions if not available
                activity,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION //needs location to connect
                ),
                1
            )
            return
        }

        _status.value = "Scanning..."  //update status
        scanner.startScan(scanCallback) //scan
    }

    fun sendStart() {
        val char = writeCharacteristic ?: return

        char.value = "s".toByteArray() //send 's' to arduino

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.writeCharacteristic(char)
            _status.value = "Streaming..."  //update status so device knows is sending and receiving values
        }
    }

    fun sendEnd() {
        val char = writeCharacteristic ?: return

        char.value = "e".toByteArray()  //send 'e' to arduino

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    fun sendCalibrate() {
        val char = writeCharacteristic ?: return

        char.value = "c".toByteArray()  //send 'c' to arduino

        if (hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            bluetoothGatt?.writeCharacteristic(char)
        }
    }

    private fun hasPermission(permission: String): Boolean {  //returns true if permission is allowed
        return ActivityCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun clearProgress(context: Context) { //delete progress from shared preferences
        val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()  //deletes all saved history in storage on app
    }
}