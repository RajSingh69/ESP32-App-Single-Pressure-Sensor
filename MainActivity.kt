package com.example.esp32pressuresensortwo

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    /**
     * This code block is used to enable permissions in the app.
     * Most common permissions include bluetooth
     * UUID_BT Value is standard, used across all esp32s
     */

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSION_LOCATION = 2
        private const val MESSAGE_READ = 3
        private val UUID_BT = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    /**
     * This code block ensures that there is an active bluetooth adapter, socket and input stream.
     * Resets these all to null to not ensure confusion.
     * The handler loops through the value of force sent and outputs it.
     */

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    private lateinit var pressureBall: View
    private lateinit var tvForceReading: TextView
    private lateinit var tvForceReadingDivided: TextView



//    private val handler = Handler(Looper.getMainLooper()) {
//        if (it.what == MESSAGE_READ) {
//            val readMessage = it.obj as String
//            tvForceReading.text = "RAW ADC Value: $readMessage"
//
//            // Convert readMessage to a Float and calculate force in kg
//            val forceInKg = try {
//                val adcValue = readMessage.toFloat() // Convert the read message to a float
//                (adcValue / 4095) * 30 // Convert the ADC value to kg
//            } catch (e: NumberFormatException) {
//                0f // Default to 0 if parsing fails
//            }
//
//            // Display the force value in kg in the second TextView
//            tvForceReadingDivided.text = "Kg Translation: $forceInKg"
//
//            true
//        } else {
//            false
//        }
//    }

    private val handler = Handler(Looper.getMainLooper()) {
        if (it.what == MESSAGE_READ) {
            val readMessage = it.obj as String
            tvForceReading.text = "RAW ADC Value: $readMessage"

            val forceInKg = try {
                val adcValue = readMessage.toFloat()
                (adcValue / 4095) * 30
            } catch (e: NumberFormatException) {
                0f
            }

            tvForceReadingDivided.text = "Kg Translation: $forceInKg"

            // Adjust the size of the ball
            adjustBallSize(forceInKg)

            true
        } else {
            false
        }
    }

    private fun adjustBallSize(forceInKg: Float) {
        // Map force value to a range suitable for the ball's size
        val minSize = 50 // Minimum size in dp
        val maxSize = 300 // Maximum size in dp

        // Calculate the size in dp based on force
        val sizeInDp = (minSize + (forceInKg * (maxSize - minSize) / 30)).toInt()

        // Convert dp to pixels
        val sizeInPx = (sizeInDp * resources.displayMetrics.density).toInt()

        // Set the new size to the ball
        val layoutParams = pressureBall.layoutParams
        layoutParams.width = sizeInPx
        layoutParams.height = sizeInPx
        pressureBall.layoutParams = layoutParams
    }



    /**
     * This code block sets the main layout of the app to activity main.
     * There is a button on and off. Used to control led on esp32 chip.
     *  Sends 0 if light to be off, 1 if light is to be on.
     * If bluetooth is not connected, asks to enable bluetooth on phone.
     * DOES NOT WORK ON VIRTUAL APP SIMULATION.
     */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pressureBall = findViewById(R.id.pressureBall)
        tvForceReading = findViewById(R.id.tvForceReading)
        tvForceReadingDivided = findViewById(R.id.tvForceReadingDivided)


        val btnOn: Button = findViewById(R.id.btnOn)
        val btnOff: Button = findViewById(R.id.btnOff)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            // Uncomment the line below to prompt the user to enable Bluetooth
            //startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
        }

        checkPermissions()

        btnOn.setOnClickListener {
            sendCommand('1')
        }

        btnOff.setOnClickListener {
            sendCommand('0')
        }
    }

    /**
     * This code block makes sure that the relevant permissions have been found and accepted.
     * It needs varying degrees of bluetooth and location to properly work.
     */

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSION_LOCATION)
        } else {
            setupBluetoothConnection()
        }
    }

    /**
     * This code block makes sure that the bluetooth connection is well established.
     * The app will only work if a device called ESP32_PressureSensor_Control_VersionTwo is
     *  found paired to the phone.
     * If not, app will request user to pair up first and THEN use the app.
     */

    private fun setupBluetoothConnection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter!!.bondedDevices
            val device = pairedDevices.find { it.name == "esp32" }

            if (device == null) {
                Toast.makeText(this, "ESP32 Pressure Sensor (V2) not paired", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_BT)
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream
                Toast.makeText(this, "Connected to ESP32", Toast.LENGTH_SHORT).show()
                ListenForData().start() // Start listening for data
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_PERMISSION_LOCATION)
        }
    }

    /**
     * THIS BLOCK DOES SOMETHING.
     */

    private fun sendCommand(command: Char) {
        try {
            bluetoothSocket?.outputStream?.write(command.toInt())
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * This block is used on the destruction of the app.
     * It closes down the bluetooth socket ready to be paired again.
     */

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_LOCATION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            setupBluetoothConnection()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    inner class ListenForData : Thread() {
        override fun run() {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (true) {
                try {
                    bytes = inputStream!!.read(buffer)
                    val readMessage = String(buffer, 0, bytes)
                    handler.obtainMessage(MESSAGE_READ, readMessage).sendToTarget()
                } catch (e: IOException) {
                    break
                }
            }
        }
    }




}
