package pl.igorwumk.drivetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

class TrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val locationList = mutableListOf<Location>()
    private var startTime: Long = 0L

    // Handler and Runnable to update notification every second
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Stop tracking and end the service
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
            }
            // Start tracking if not already tracking
            ACTION_START -> {
                if (startTime == 0L) {
                    startTracking()
                }
            }
        }
        // Return START_STICKY so the service keeps running even if app is closed
        return START_STICKY
    }

    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) {
                    locationList.add(location)
                }
                // Update notification with current distance and time
                updateNotification()
            }
        }
    }

    private fun startTracking() {
        // Check for permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, stop the service
            stopSelf()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }

        startTime = System.currentTimeMillis()
        // Configure location request
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // Start updating the notification
        timerHandler.post(timerRunnable)

        // Start as foreground service with initial notification
        startForeground(NOTIFICATION_ID, buildNotification(0, 0.0))
    }

    private fun stopTracking() {
        // Remove location updates and stop the timer
        fusedLocationClient.removeLocationUpdates(locationCallback)
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun calculateTotalDistance(): Double {
        var totalDistance = 0.0
        if (locationList.size < 2) return totalDistance
        for (i in 1 until locationList.size) {
            totalDistance += locationList[i - 1].distanceTo(locationList[i])
        }
        return totalDistance
    }

    private fun getElapsedTimeSeconds(): Long {
        return (System.currentTimeMillis() - startTime) / 1000
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun buildNotification(elapsedSeconds: Long, distanceInMeters: Double): Notification {
        val distanceKm = distanceInMeters / 1000.0
        val timeFormatted = formatTime(elapsedSeconds)
        val contentText = "Time: $timeFormatted | Distance: %.2f km".format(distanceKm)

        // PendingIntent to trigger STOP action
        val stopIntent = Intent(this, TrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracking Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_tracking)
            .addAction(R.drawable.ic_stop, "STOP", stopPendingIntent)
            .setOngoing(true) // Makes notification non-dismissible
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification() {
        val elapsedSeconds = getElapsedTimeSeconds()
        val totalDistance = calculateTotalDistance()
        val notification = buildNotification(elapsedSeconds, totalDistance)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        // Clean up updates
        stopTracking()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val ACTION_START = "pl.igorwumk.drivetracker.action.START"
        const val ACTION_STOP = "pl.igorwumk.drivetracker.action.STOP"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "tracking_channel"
    }
}