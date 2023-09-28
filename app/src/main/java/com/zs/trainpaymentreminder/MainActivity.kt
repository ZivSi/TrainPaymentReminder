package com.zs.trainpaymentreminder

import android.Manifest
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
    lateinit var coordinatesTextView: TextView
    lateinit var addressTextView: TextView
    lateinit var closestStationTextView: TextView
    lateinit var velocityTextView: TextView
    private var notificationSwitch: SwitchCompat? = null
    private var vibrateSwitch: SwitchCompat? = null
    private var popupSwitch: SwitchCompat? = null

    lateinit var currentLocation: Location

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val locationPermissionsRequestCode = 1001
    private val postNotificationsPermissionRequestCode = 1002

    private lateinit var locationReceiver: BroadcastReceiver


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initListeners()

        val intentFilter = IntentFilter("LocationResult")
        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, intentFilter)

        checkAndAskPermissions()

        setSwitches()

        if (!serviceIsRunning(this)) {
            startService()

            // startPopUp();
        }
    }

    override fun onResume() {
        super.onResume()

        checkAndAskPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()

        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == locationPermissionsRequestCode || requestCode == postNotificationsPermissionRequestCode) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Some permissions denied. Can't start", Toast.LENGTH_LONG)
                    .show()

                checkAndAskPermissions()
            } else {
                if (!serviceIsRunning(this)) {
                    startService()
                }
            }
        }
    }

    private fun checkAndAskPermissions() {

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 99
            )
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 98
            )
        }
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 95
            )
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 94
            )
        }
    }

    private fun startService(action: String = START_TRACKING) {
        val intent = Intent(this, AppService::class.java)
        intent.action = action
        startService(intent)
    }

    private fun serviceIsRunning(context: Context): Boolean {
        val serviceClass = AppService::class.java

        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun initViews() {
        sharedPreferences = getSharedPreferences("NotificationPreferences", Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        notificationSwitch = findViewById(R.id.notificationSwitch)
        vibrateSwitch = findViewById(R.id.vibrateSwitch)
        popupSwitch = findViewById(R.id.popupSwitch)

        coordinatesTextView = findViewById(R.id.coordinatesTextView)
        addressTextView = findViewById(R.id.addressTextView)
        closestStationTextView = findViewById(R.id.closestStationTextView)
        velocityTextView = findViewById(R.id.velocityTextView)
    }

    private fun initListeners(): Unit {
        locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "LocationResult") {
                    currentLocation = intent.getParcelableExtra("location")!!
                    val currentSpeed = intent.getIntExtra("speed", -1)

                    coordinatesTextView.text = "Coordinates:  $currentLocation"
                    addressTextView.text = "Address: ${getAddress(baseContext, currentLocation)}"
                    closestStationTextView.text = generateSequence(getClosestStation())
                    velocityTextView.text =
                        "Velocity: $currentSpeed Km/h " + (if (intent.getBooleanExtra(
                                "iswalking", false
                            )
                        ) "(walking)" else "(not walking)")

                    formatLines()
                }
            }
        }

        notificationSwitch?.setOnCheckedChangeListener { buttonView, isChecked ->
            editor.putBoolean("notificationsEnabled", isChecked)
            editor.apply()

            vibrateSwitch?.isEnabled = isChecked
            popupSwitch?.isEnabled = isChecked
        }

        vibrateSwitch?.setOnCheckedChangeListener { buttonView, isChecked ->
            editor.putBoolean("vibrationEnabled", isChecked)
            editor.apply()
        }

        popupSwitch?.setOnCheckedChangeListener { buttonView, isChecked ->
            editor.putBoolean("popupEnabled", isChecked)
            editor.apply()
        }
    }

    private fun setSwitches() {
        notificationSwitch?.isChecked = sharedPreferences.getBoolean("notificationsEnabled", true)
        vibrateSwitch?.isChecked = sharedPreferences.getBoolean("vibrationEnabled", true)
        popupSwitch?.isChecked = sharedPreferences.getBoolean("popupEnabled", true)
    }

    private fun startPopUp() {
        Thread {
            startService(SHOW_POPUP)
        }.start()
    }

    private fun formatLines(): Unit {
        if (coordinatesTextView.text.length > 31) {
            val length = coordinatesTextView.text.length
            coordinatesTextView.text = coordinatesTextView.text.substring(
                0, 30
            ) + "\n" + coordinatesTextView.text.substring(30, length)
        }

        if (addressTextView.text.length > 31) {
            val length = addressTextView.text.length
            addressTextView.text =
                addressTextView.text.substring(0, 30) + "\n" + addressTextView.text.substring(
                    30, length
                )
        }

        if (closestStationTextView.text.length > 45) {
            val length = closestStationTextView.text.length
            closestStationTextView.text = closestStationTextView.text.substring(
                0, 45
            ) + "\n" + closestStationTextView.text.substring(45, length)
        }
    }

    private fun generateSequence(closestStation: ClosestStationResult): String {
        if (closestStation.distance > 1000) {
            return "Closest station: " + closestStation.station.name + "(" + (closestStation.distance / 1000).toInt()
                .toString() + " Km)"
        }

        return "Closest station: " + closestStation.station.name + "(" + closestStation.distance.toInt()
            .toString() + " M)"
    }

    fun getAddress(context: Context, location: Location?): String {
        if (location == null) {
            return "No Address Found"
        }

        val latitude = location.latitude
        val longitude = location.longitude

        val geocoder = Geocoder(context, Locale.getDefault())
        var result = ""

        try {
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address: Address = addresses[0]
                val addressLines = mutableListOf<String>()

                for (i in 0..address.maxAddressLineIndex) {
                    addressLines.add(address.getAddressLine(i))
                }

                result = addressLines.joinToString(separator = "\n")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return result
    }

    private fun getClosestStation(): ClosestStationResult {
        var closestStation = GlobalObjectList.stationsList[0]
        var smallestDistance = Double.MAX_VALUE

        for (station in GlobalObjectList.stationsList) {
            val currentDistance =
                station.distanceTo(currentLocation.latitude, currentLocation.longitude)

            if (currentDistance < smallestDistance) {
                smallestDistance = currentDistance
                closestStation = station
            }
        }

        return ClosestStationResult(closestStation, smallestDistance)
    }
}