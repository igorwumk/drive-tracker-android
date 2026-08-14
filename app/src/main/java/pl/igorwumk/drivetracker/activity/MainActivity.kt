package pl.igorwumk.drivetracker.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import pl.igorwumk.drivetracker.LoginDialog
import pl.igorwumk.drivetracker.R
import pl.igorwumk.drivetracker.database.TrackingSessionWithSegments
import pl.igorwumk.drivetracker.api.APIService
import pl.igorwumk.drivetracker.api.FullTrackingSessionDto
import pl.igorwumk.drivetracker.api.RetrofitClient
import pl.igorwumk.drivetracker.api.TrackingPointUploadDto
import pl.igorwumk.drivetracker.api.TrackingSegmentUploadDto
import pl.igorwumk.drivetracker.api.TrackingSessionUploadDto
import pl.igorwumk.drivetracker.database.TrackingDatabase
import pl.igorwumk.drivetracker.database.entity.TrackingPoint
import pl.igorwumk.drivetracker.database.entity.TrackingSegment
import pl.igorwumk.drivetracker.database.entity.TrackingSession
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

    // DRF communication and auth
    private lateinit var authService: APIService
    private val prefs by lazy { EncryptedPrefs.get(this) }

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
                pauseResumeButton.text = if (service.isPaused) getString(R.string.button_resume) else getString(R.string.button_pause)
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

        // Set UI text for param strings
        tvTime.text = getString(R.string.tracking_elapsed_time, 0, 0, 0)
        tvDistance.text = getString(R.string.tracking_distance_kilometers, 0.0)

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
                    pauseResumeButton.setText(R.string.button_pause)
                } else {
                    // Pause tracking
                    val pauseIntent = Intent(this, TrackingService::class.java).apply {
                        action = TrackingService.ACTION_PAUSE
                    }
                    startService(pauseIntent)
                    pauseResumeButton.setText(R.string.button_resume)
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

        // Retrofit setup for API
        authService = RetrofitClient.instance.create(APIService::class.java)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        //return super.onCreateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item. itemId) {
            R.id.action_sync -> {
                onSyncClicked()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSyncClicked() {
        val token = prefs.getToken()
        if (token.isNullOrEmpty()) {
            // no token - show login dialog
            LoginDialog().show(supportFragmentManager, "LoginDialog")
        } else {
            doSyncWithServer(token)
        }
    }

    fun doSyncWithServer(token: String) = lifecycleScope.launch {
        val auth = "Token $token"
        val db = TrackingDatabase.getDatabase(this@MainActivity)
        val dao = db.sessionDao()
        val api = RetrofitClient.instance.create(APIService::class.java)

        // reset counters and inflate dialog
        RetrofitClient.TrafficStats.reset()
        val dlgView = layoutInflater.inflate(R.layout.dialog_sync_traffic, null)
        val tvSent = dlgView.findViewById<TextView>(R.id.tv_sync_sent)
        val tvRec = dlgView.findViewById<TextView>(R.id.tv_sync_received)
        val dialog = AlertDialog.Builder(this@MainActivity)
            .setTitle(getString(R.string.account_sync_in_progress))
            .setView(dlgView)
            .setCancelable(false)
            .show()
        suspend fun updateTrafficUI() = withContext(Dispatchers.Main) {
            tvSent.text = getString(R.string.network_sent_kilobytes, RetrofitClient.TrafficStats.bytesSent / 1024)
            tvRec.text = getString(R.string.network_received_kilobytes, RetrofitClient.TrafficStats.bytesReceived / 1024)
        }

        try {
            // Fetch shallow list
            val listResp = api.listTrackings(auth)
            updateTrafficUI()
            if (listResp.code() == 401) { LoginDialog().show(supportFragmentManager, "Login"); return@launch }
            val remoteList = listResp.body() ?: emptyList()

            // Load all sessions into memory
            //val localAll = dao.getAllSessions()
            val toDelete = mutableListOf<Pair<Long, Long>>() // remoteId + localSessionId

            // Process each remote session
            for (remote in remoteList) {
                val local = dao.findByUniqueness(remote.startTime, remote.totalDistance, remote.totalTime, remote.timezone)
                if (local == null) {
                    // new session - download in full
                    val detail = api.getTrackingDetail(remote.id, auth)
                    updateTrafficUI()
                    if (detail.code() == 401) { LoginDialog().show(supportFragmentManager, "Login"); return@launch }
                    detail.body()?.let { full ->
                        // convert and insert into Room database
                        val localId = saveFullRemote(full, db)
                        dao.markSynced(localId)
                    }
                } else {
                    // already exists in Room database, check status
                    when (local.status) {
                        "desynced" -> dao.markSynced(local.sessionId)
                        "deletePending" -> toDelete += remote.id to local.sessionId
                        "synced" -> { /* all good */ }
                        "syncPending" -> {
                            // unexpected state - set synced and warning to logcat
                            if (local.locale == remote.locale) {
                                dao.markSynced(local.sessionId)
                            }
                            Log.w("Sync", "Unexpected syncPending for session ${remote.id}" +
                                if (local.locale != remote.locale)
                                " (local=${local.locale}, remote=${remote.locale}" else ""
                            )
                        }
                    }
                }
            }

            // Delete on server and mark deleted locally
            toDelete.forEach { (remoteId, localId) ->
                val del = api.deleteTracking(remoteId, auth)
                updateTrafficUI()
                if (del.code() == 401) { LoginDialog().show(supportFragmentManager, "Login"); return@launch }
                if (del.isSuccessful) dao.markDeleted(localId)
            }

            // Upload local syncPending and desynced sessions
            val pending = dao.getAllByStatus("syncPending") + dao.getAllByStatus("desynced")
            for (local in pending) {
                val full = db.sessionDao().getSessionWithSegments(local.sessionId)
                val upload = full!!.toUploadDto() // map to TrackingSessionUploadDto
                val resp = api.createTracking(upload, auth)
                updateTrafficUI()
                if (resp.code() == 401) { LoginDialog().show(supportFragmentManager, "Login"); return@launch }
                if (resp.isSuccessful) dao.markSynced(local.sessionId)
            }


            Toast.makeText(this@MainActivity, getString(R.string.account_sync_complete), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, getString(R.string.account_sync_error, e.localizedMessage), Toast.LENGTH_LONG).show()
        } finally {
            // purge local deleted sessions
            withContext(Dispatchers.IO) {
                dao.deleteByStatus("deleted")
            }
            dialog.dismiss()
        }
    }

    suspend fun saveFullRemote(full: FullTrackingSessionDto, db: TrackingDatabase): Long {
        // insert session
        val session = TrackingSession(
            startTime = full.startTime,
            totalDistance = full.totalDistance,
            totalTime = full.totalTime,
            timezone = full.timezone,
            locale = full.locale,
            status = "synced"
        )
        val id = db.sessionDao().insertSession(session)
        // insert segments and points
        full.segments.forEach { segDto ->
            val seg = TrackingSegment(sessionId = id, segmentOrder = segDto.segmentOrder)
            val segId = db.segmentDao().insertSegment(seg)
            val pts = segDto.points.map { TrackingPoint(
                segmentId = segId,
                latitude = it.latitude,
                longitude = it.longitude,
                timestamp = it.timestamp,
                pointOrder = it.pointOrder
            ) }
            db.pointDao().insertPoints(pts)
        }
        return id
    }

    fun TrackingSessionWithSegments.toUploadDto(): TrackingSessionUploadDto {
        return TrackingSessionUploadDto(
            startTime = session.startTime,
            totalDistance = session.totalDistance,
            totalTime = session.totalTime,
            timezone = session.timezone,
            locale = session.locale,
            segments = segments.map { seg ->
                TrackingSegmentUploadDto(
                    points = seg.points.map { pt ->
                        TrackingPointUploadDto(pt.latitude, pt.longitude, pt.timestamp, pt.pointOrder)
                    },
                    segmentOrder = seg.segment.segmentOrder
                )
            }
        )
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
        pauseResumeButton.setText(R.string.button_pause)
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
        tvTime.text = getString(R.string.tracking_elapsed_time, hours, minutes, seconds)
    }

    // Format and update distance
    private fun updateDistanceTravelled(distanceInMeters: Double = 0.0) {
        val distanceKm = distanceInMeters / 1000.0
        tvDistance.text = getString(R.string.tracking_distance_kilometers, distanceKm)
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
            val name = getString(R.string.tracking_channel_name)
            val descriptionText = getString(R.string.tracking_channel_description)
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

object EncryptedPrefs {
    private const val NAME = "secure_prefs"
    private const val KEY_TOKEN = "user_token"
    private const val KEY_USERNAME = "user_name"

    fun get(context: Context): SharedPrefs {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val sp = EncryptedSharedPreferences.create(
            context, NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return SharedPrefs(sp)
    }

    class SharedPrefs(private val sp: SharedPreferences) {
        fun saveCredentials(username: String, token: String) {
            sp.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_TOKEN, token)
                .apply()
        }
        fun getToken(): String? = sp.getString(KEY_TOKEN, null)
        fun getUsername(): String? = sp.getString(KEY_USERNAME, null)
        fun clearCredentials() {
            sp.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_TOKEN)
                .apply()
        }
    }
}