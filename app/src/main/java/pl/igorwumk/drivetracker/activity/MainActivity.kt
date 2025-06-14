package pl.igorwumk.drivetracker.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import pl.igorwumk.drivetracker.R
import pl.igorwumk.drivetracker.service.TrackingService

interface PermissionRequestCallback {
    fun requestTrackingPermission()
    fun requestNotificationPermission()
}

class MainActivity : BaseDrawerActivity(), PermissionRequestCallback {
    private lateinit var mapView: MapView
    private lateinit var tvTime: TextView
    private lateinit var tvDistance: TextView
    private lateinit var startButton: Button
    private lateinit var pauseResumeButton: Button
    private lateinit var stopButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // Service variables
    private var trackingService: TrackingService? = null
    private var serviceBound = false

    // Handler to update the UI from the service
    private val uiUpdateHandler = Handler(Looper.getMainLooper())
    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            if (serviceBound && trackingService != null) {
                val elapsed = trackingService!!.getElapsedTimeSeconds()
                val distance = trackingService!!.getTotalDistance()
                updateTimeElapsed(elapsed)
                updateDistanceTravelled(distance)
                // Update the map path
                drawPathOnMap(trackingService!!.getPathSegments())
            }
            uiUpdateHandler.postDelayed(this, 1000)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TrackingService.LocalBinder
            trackingService = binder.getService()
            serviceBound = true
            // Register the permissions callback
            trackingService?.setPermissionRequestCallback(this@MainActivity)
            // Update the UI buttons at connection and setup updates
            updateUIFromService()
            trackingService?.setTrackingStateChangeListener {
                runOnUiThread { updateUIFromService() }
            }
            // Start updating UI when bound
            uiUpdateHandler.post(uiUpdateRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            trackingService = null
        }
    }

    private fun updateUIFromService() {
        trackingService?.let { service ->
            if (service.isTracking) {
                // Tracking active -> show pause/stop buttons
                startButton.visibility = View.GONE
                findViewById<LinearLayout>(R.id.pauseStopBar).visibility = View.VISIBLE
                // Update text of pause/resume button
                pauseResumeButton.text = if (service.isPaused) "RESUME" else "PAUSE"
            } else {
                // Not tracking -> show start button
                startButton.visibility = View.VISIBLE
                findViewById<LinearLayout>(R.id.pauseStopBar).visibility = View.GONE
            }
        } ?: run {
            // Ensure default UI state if service is null
            startButton.visibility = View.VISIBLE
            findViewById<LinearLayout>(R.id.pauseStopBar).visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        // Setup osmdroid
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        // Inflate the layout
        //setContentView(R.layout.activity_main)
        layoutInflater.inflate(R.layout.activity_main, findViewById(R.id.content_frame), true)

        // Check for tracking permissions and initialize osmdroid MapView
        requestTrackingPermission()
        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        val mapController: IMapController = mapView.controller
        mapController.setZoom(17.0)
        setupLocationOverlay()

        // Bind UI elements
        tvTime = findViewById(R.id.tvTime)
        tvDistance = findViewById(R.id.tvDistance)
        startButton = findViewById(R.id.startButton)
        pauseResumeButton = findViewById(R.id.pauseResumeButton)
        stopButton = findViewById(R.id.stopButton)

        // At start only start button visible
        startButton.visibility = View.VISIBLE

        // Create notification channel
        createNotificationChannel(this)

        // Initialize LocationProvider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup onClick listeners for the buttons
        startButton.setOnClickListener {
            val startIntent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            // startForegroundService for Android 0 and above
            ContextCompat.startForegroundService(this, startIntent)
            // Bind if not bound
            bindToTrackingService()
            updateUIForTrackingStarted()
        }
        pauseResumeButton.setOnClickListener {
            trackingService?.let {
                if (it.isPaused) {
                    // Resume tracking
                    val resumeIntent = Intent(this, TrackingService::class.java).apply {
                        action = TrackingService.ACTION_RESUME
                    }
                    startService(resumeIntent)
                    pauseResumeButton.text = "PAUSE"
                } else {
                    // Pause tracking
                    val pauseIntent = Intent(this, TrackingService::class.java).apply {
                        action = TrackingService.ACTION_PAUSE
                    }
                    startService(pauseIntent)
                    pauseResumeButton.text = "RESUME"
                }
            }
        }
        stopButton.setOnClickListener {
            val stopIntent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP
            }
            startService(stopIntent)
            //unbindService(serviceConnection)
            updateUIForTrackingStopped()
        }
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.activity_with_drawer
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            trackingService?.setTrackingStateChangeListener(null)
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!serviceBound) {
            bindToTrackingService()
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        uiUpdateHandler.removeCallbacks(uiUpdateRunnable)
    }

    private fun bindToTrackingService() {
        Intent(this, TrackingService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun updateUIForTrackingStarted() {
        startButton.visibility = View.GONE
        // Show pause/resume + stop buttons
        findViewById<LinearLayout>(R.id.pauseStopBar).visibility = View.VISIBLE
        pauseResumeButton.text = "PAUSE"
    }

    private fun updateUIForTrackingStopped() {
        startButton.visibility = View.VISIBLE
        findViewById<LinearLayout>(R.id.pauseStopBar).visibility = View.GONE
    }

    // Initialize rendering of current position on a map
    private fun setupLocationOverlay() {
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay.enableMyLocation()
        myLocationOverlay.enableFollowLocation()
        mapView.overlays.add(myLocationOverlay)
    }

    // Update UI stats
    private fun updateUIStats(trackingDuration: Long = 0L, distanceInMeters: Double = 0.0) {
        updateTimeElapsed(trackingDuration)
        updateDistanceTravelled(distanceInMeters)
    }

    // Format and update time duration
    private fun updateTimeElapsed(trackingDuration: Long = 0L) {
        val hours = (trackingDuration / 3600).toInt()
        val minutes = ((trackingDuration % 3600) / 60).toInt()
        val seconds = (trackingDuration % 60).toInt()
        tvTime.text = String.format("Time: %02d:%02d:%02d", hours, minutes, seconds)
    }

    // Format and update distance
    private fun updateDistanceTravelled(distanceInMeters: Double = 0.0) {
        val distanceKm = distanceInMeters / 1000.0
        tvDistance.text = String.format("Distance: %.2f km", distanceKm)
    }

    // Draw the path on a map
    private fun drawPathOnMap(segments: List<List<Location>>) {
        if (segments.isEmpty()) return

        mapView.overlays.removeAll { it is Polyline }

        for (segment in segments) {
            val geoPoints = segment.map { GeoPoint(it.latitude, it.longitude) }
            val polyLine = Polyline().apply {
                setPoints(geoPoints)
                color = Color.RED
                width = 5.0f
            }
            mapView.overlays.add(polyLine)
        }

        mapView.invalidate()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Tracking Channel"
            val descriptionText = "Channel for tracking notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(TrackingService.CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(false)
                enableVibration(false)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun requestTrackingPermission() {
        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    override fun requestNotificationPermission() {
        // Check for notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}
