package com.zs.trainpaymentreminder

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlin.math.abs

const val GET_LOCATION = "get current location"
const val START_TRACKING = "start tracking"
const val STOP_TRACKING = "stop tracking"
const val SHOW_POPUP = "show popup card"
const val SNOOZE = "sleep"

const val CHANNEL_ID = "CHANNEL_ID"
const val NOTIFICATION_ID = 1234
const val NOTIFICATION_ID_2 = 12345

const val METER = 1
const val KM = 1000 * METER

const val VERY_CLOSE_DISTANCE = 200 * METER
const val CLOSE_DISTANCE = 500 * METER
const val PRETTY_CLOSE = 700 * METER

const val WALKING_SPEED = 6 // Km/h

const val NOTIFICATION_PREFERENCES = "NotificationPreferences"

private var activeNotification: NotificationCompat.Builder? = null


class AppService : Service(), SensorEventListener {
    private var run = false
    private var currentLocation = Location(0.0, 0.0)
    private var currentSpeed = 0
    private var sleepTime = 2

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var notification: Notification

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private lateinit var sensorManager: SensorManager
    private lateinit var rotationSensor: Sensor
    private var sensorsXData = CircularBuffer<Float>(50)
    private var sensorsYData = CircularBuffer<Float>(50)
    private var sensorsZData = CircularBuffer<Float>(50)

    private var lastX = -1f
    private var lastY = -1f
    private var lastZ = -1f

    private var isWalking = false

    private var sensor = Sensor.TYPE_ORIENTATION

    private lateinit var handler: Handler

    private var calculateSensors = false
    private var notificationUpdated = false

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        customizeForegroundNotification()

        // azimuth -170 < x < 150
        // pitch -100 < x < 100
        // Roll -200 < x < 100

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(sensor)

        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL)

        handler = Handler(Looper.getMainLooper())

        setCallBack()

        sharedPreferences = getSharedPreferences(NOTIFICATION_PREFERENCES, Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()
    }

    private fun setCallBack() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    // Update your current location and speed here
                    currentLocation.latitude = location.latitude
                    currentLocation.longitude = location.longitude
                    currentSpeed = (location.speed * 3.6).toInt()

                    passDataToActivity()

                    stopLocationUpdates()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            GET_LOCATION -> {
                passDataToActivity()
            }
            START_TRACKING -> {
                if (!run) {
                    GlobalObjectList.delay =
                        10 // Give time for the algorithm to understand that you are walking / on the train / near the station
                    run = true
                    startLocationLoop()

                    startForeground(NOTIFICATION_ID, notification)
                }
            }
            STOP_TRACKING -> run = false

            SNOOZE -> GlobalObjectList.delay = 30 * 60

            SHOW_POPUP -> {

            }
        }

        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()

        stopLocationUpdates()
        sensorManager.unregisterListener(this)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 3000L
            fastestInterval = 500L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun passDataToActivity() {
        val intent = Intent("LocationResult")
        intent.putExtra("location", currentLocation)
        intent.putExtra("speed", currentSpeed)
        intent.putExtra("iswalking", isWalking)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun startLocationLoop() {
        Thread {
            while (true) {
                sleepTime = maxOf(sleepTime, 0)

                Thread.sleep((sleepTime * 1000).toLong())

                if (GlobalObjectList.delay > 0) {
                    GlobalObjectList.delay -= sleepTime

                    GlobalObjectList.delay = maxOf(GlobalObjectList.delay, 0)
                    continue
                }

                requestLocationUpdates()

                val closestStation = getClosestStation()

                if (closeToStation(closestStation.distance)) {
                    calculateSensors = true
                    notificationUpdated = false

                    if (onTheTrain() && vibrationEnabled()) {
                        vibrate(baseContext, 6, 200)
                        showPaymentNotification()
                    }

                } else {
                    calculateSensors = false
                    notificationUpdated = false
                }

                showNotification(generateSequence(closestStation.distance))

                sleepTime = determineSleepTime(closestStation.distance)
            }
        }.start()
    }

    private fun determineSleepTime(distance: Double): Int {
        return when {
            distance >= KM -> 30
            distance > CLOSE_DISTANCE -> 10
            distance > VERY_CLOSE_DISTANCE -> 5
            else -> 3
        }
    }

    private fun onTheTrain(): Boolean {
        return currentSpeed > WALKING_SPEED && !isWalking()
    }

    private fun isWalking(): Boolean {
        if (this.currentSpeed > WALKING_SPEED) {
            return false
        }

        var xResult = false
        var yResult = false
        var zResult = false

        var xSum = 0f
        var ySum = 0f
        var zSum = 0f

        val x = Thread {
            xSum = sensorsXData.getData().sum()
            xResult = xSum < sensorsXData.getSize() * 5
        }

        val y = Thread {
            ySum = sensorsYData.getData().sum()
            yResult = ySum < sensorsYData.getSize() * 7
        }

        val z = Thread {
            zSum = sensorsZData.getData().sum()
            zResult = zSum < sensorsZData.getSize() * 8.5
        }

        x.start()
        y.start()
        z.start()

        x.join()
        y.join()
        z.join()

        Log.d("values_boolean", "X: $xResult, Y: $yResult, Z: $zResult")
        Log.d("sum", "X: $xSum, Y: $ySum, Z: $zSum")

        isWalking = !xResult || !yResult || !zResult

        if (isWalking && false) {
            var intent = Intent(this, MainActivity::class.java)
            intent.action = SHOW_POPUP
            onStartCommand(intent, 0, 0)
        }

        return isWalking
    }

    private fun vibrationEnabled(): Boolean {
        return sharedPreferences.getBoolean("vibrationEnabled", true)
    }

    private fun notificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean("notificationsEnabled", true)
    }

    private fun closeToStation(distance: Double): Boolean {
        return distance <= CLOSE_DISTANCE
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Background Notification", NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun customizeForegroundNotification() {
        notification =
            NotificationCompat.Builder(baseContext, CHANNEL_ID).setContentTitle("Payment Reminder")
                .setContentText("Running in the background").setSmallIcon(R.drawable.train_station)
                .build()
    }

    private fun updateConstantNotification(sensorsChecked: Boolean) {
        val notificationManager = NotificationManagerCompat.from(baseContext)

        val updatedNotification =
            NotificationCompat.Builder(baseContext, CHANNEL_ID).setContentTitle("Payment Reminder")
                .setContentText(if (sensorsChecked) "Sensors are checked" else "Sensors not checked")
                .setSmallIcon(R.drawable.train_station).build()

        notificationManager.notify(NOTIFICATION_ID, updatedNotification)
    }

    private fun showNotification(content: String) {
        if (!notificationsEnabled()) {
            hideNotification()
            return
        }

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (activeNotification == null) {
            // Create a notification channel (if not already created)
            val channel = NotificationChannel(
                CHANNEL_ID, "Distance Notifications", NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)

            activeNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        // Update the content of the active notification
        activeNotification?.setContentTitle("Distance From Station")
        activeNotification?.setContentText(content)

        notificationManager.notify(NOTIFICATION_ID_2, activeNotification?.build())
    }

    // Helper function to cancel/hide the notification
    private fun hideNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_2)
    }

    // Show "Time to pay" notification
    private fun showPaymentNotification() {
        val channelId = "channel2"
        val notificationId = 12349

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create a notification channel
        val channel = NotificationChannel(
            channelId, "Payment Notifications", NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_baseline_notifications_active_24)
            .setContentTitle("Time to pay").setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun generateSequence(distance: Double): String {
        return when {
            distance <= PRETTY_CLOSE -> "There is station ahead of you"
            distance <= VERY_CLOSE_DISTANCE -> "You are close to a station"
            distance <= CLOSE_DISTANCE -> "You are pretty close to a station"
            else -> "You are not close to a station"
        }
    }

    private fun vibrate(context: Context, times: Int, length: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Check if the device supports vibration
        if (vibrator.hasVibrator()) {
            // Create a pattern for the vibration
            val pattern = LongArray((times * 2) - 1) { length }

            // Vibrate with the specified pattern and default attributes
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!notificationUpdated) {
            notificationUpdated = true
            updateConstantNotification(calculateSensors)
        }

        if (!calculateSensors) {
            return
        }

        if (event?.sensor?.type == sensor) {
            val rotationEvent = event.values

            val eventX = rotationEvent[0]
            val eventY = rotationEvent[1]
            val eventZ = rotationEvent[2]

            if (lastX < 0) {
                lastX = eventX
                lastY = eventY
                lastZ = eventZ

                return
            }

            val changeX = abs(eventX - lastX)
            val changeY = abs(eventY - lastY)
            val changeZ = abs(eventZ - lastZ)

            sensorsXData.push(changeX)
            sensorsYData.push(changeY)
            sensorsZData.push(changeZ)

            lastX = eventX
            lastY = eventY
            lastZ = eventZ

            Log.d("Sensor", "X: $changeX, Y: $changeY, Z: $changeZ")
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
    }
}