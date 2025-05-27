package pl.igorwumk.drivetracker

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
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.material.navigation.NavigationView
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.library.BuildConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import pl.igorwumk.drivetracker.ui.theme.DriveTrackerTheme
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var navView: NavigationView

    private lateinit var mapView: MapView
    private lateinit var tvTime: TextView
    private lateinit var tvDistance: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // List to store tracked locations
    private val locationList = mutableListOf<Location>()
    private var tracking = false

    // Service variables
    private var trackingService: TrackingService? = null
    private var serviceBound = false

    // For updating time elapsed
    private var startTime: Long = 0L
    private val timerHandler = Handler(Looper.getMainLooper())

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            Log.d("MainActivity", "Received ${result.locations.size} locations")
            for (location in result.locations) {
                locationList.add(location)
                // Update the map
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.animateTo(geoPoint)
            }
            drawPathOnMap()
        }
    }

    // Runnable that updates the timer every second
    private val timerRunnable = object : Runnable {
        override fun run() {
            // Calculate elapsed time and update UI
            val elapsedMilis = System.currentTimeMillis() - startTime
            val elapsedSeconds = elapsedMilis / 1000
            updateTimeElapsed(elapsedSeconds)

            // Post this runnable again after 1 second
            timerHandler.postDelayed(this, 1000)
        }
    }

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
                drawPathOnMap(trackingService!!.getLocationList())
            }
            uiUpdateHandler.postDelayed(this, 1000)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TrackingService.LocalBinder
            trackingService = binder.getService()
            serviceBound = true
            // Start updating UI when bound
            uiUpdateHandler.post(uiUpdateRunnable)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            trackingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //enableEdgeToEdge()
        /*setContent {
            DriveTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                    OsmdroidMapView()
                }
            }
        }*/
        // Setup osmdroid
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        //Configuration.getInstance().userAgentValue = "42"

        // Inflate the layout
        setContentView(R.layout.activity_main)

        // Setup toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Bind layout and nav
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        // Setup hamburger button
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Listen for navigation item selections
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawers()
            true
        }

        // Initialize osmdroid MapView
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
        stopButton = findViewById(R.id.stopButton)

        // Create notification channel
        createNotificationChannel(this)

        // Initialize LocationProvider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Setup onClick listeners for the buttons
        startButton.setOnClickListener {
            //startTracking()
            //createNotificationChannel(this)
            val startIntent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_START
            }
            // startForegroundService for Android 0 and above
            ContextCompat.startForegroundService(this, startIntent)
            // Bind if not bound
            bindToTrackingService()
        }
        stopButton.setOnClickListener {
            //stopTrackingAndSaveGPX()
            val stopIntent = Intent(this, TrackingService::class.java).apply {
                action = TrackingService.ACTION_STOP
            }
            startService(stopIntent)
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

    // Calculate total distance of locationList
    private fun calculateTotalDistance(): Double {
        var totalDistance = 0.0
        if (locationList.size < 2) return totalDistance
        for (i in 1 until locationList.size) {
            totalDistance += locationList[i - 1].distanceTo(locationList[i])
        }
        return totalDistance
    }

    @Deprecated("Service/Activity decoupling")
    private fun drawPathOnMap() {
        drawPathOnMap(locationList)
    }

    // Draw the path on a map
    private fun drawPathOnMap(locations: List<Location>) {
        if (locations.isEmpty()) return

        val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }

        val polyLine = Polyline().apply {
            setPoints(geoPoints)
            color = Color.RED
            width = 5.0f
        }

        mapView.overlays.removeAll { it is Polyline }
        mapView.overlays.add(polyLine)
        mapView.invalidate()

        // Calculate distance and update UI
        val totalDistance = calculateTotalDistance()
        //updateDistanceTravelled(totalDistance)
    }

    // Start location tracking
    @Deprecated("Activity/Service decoupling")
    private fun startTracking() {
        if (tracking) return

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
        else {
            tracking = true
            locationList.clear()

            val locationRequest = LocationRequest.create().apply {
                interval = 5000 // 5 seconds updates
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Initiate time tracking
            startTime = System.currentTimeMillis()
            updateTimeElapsed()
            timerHandler.postDelayed(timerRunnable,0)

            Toast.makeText(this, "Tracking started", Toast.LENGTH_SHORT).show()
        }
    }

    // Stop tracking and save the GPX data
    @Deprecated("Activity/Service decoupling")
    private fun stopTrackingAndSaveGPX() {
        if (!tracking) return

        tracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Stop the timer
        timerHandler.removeCallbacks(timerRunnable)

        if (locationList.isEmpty()) {
            Toast.makeText(this, "No location updates received!", Toast.LENGTH_LONG).show()
            return
        }

        // Generate GPX string data
        val gpxData = generateGPX(locationList)

        // Store files in Downloads folder
        val publicDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val fileName = "track_${System.currentTimeMillis()}.gpx"
        val gpxFile = File(publicDir, fileName)

        // Save the GPX data
        try {
            gpxFile.writeText(gpxData)
            Toast.makeText(this, "GPX saved as $fileName", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            Toast.makeText(this, "Failed to save GPX: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Generate GPX XML string from LocationList
    private fun generateGPX(locations: List<Location>): String {
        if (locations.isEmpty()) return ""

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val gpxBuilder = StringBuilder()
        gpxBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        gpxBuilder.append("<gpx version=\"1.1\" creator=\"YourAppName\">\n")
        gpxBuilder.append("  <trk>\n    <trkseg>\n")

        for (location in locations) {
            gpxBuilder.append("      <trkpt lat=\"${location.latitude}\" lon=\"${location.longitude}\">\n")
            gpxBuilder.append("        <time>${sdf.format(Date(location.time))}</time>\n")
            gpxBuilder.append("      </trkpt>\n")
        }
        gpxBuilder.append("    </trkseg>\n  </trk>\n")
        gpxBuilder.append("</gpx>")
        return gpxBuilder.toString()
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
}
