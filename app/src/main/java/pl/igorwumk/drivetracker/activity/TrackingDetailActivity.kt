package pl.igorwumk.drivetracker.activity

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import pl.igorwumk.drivetracker.R
import pl.igorwumk.drivetracker.database.TrackingDatabase
import pl.igorwumk.drivetracker.database.TrackingSegmentWithPoints
import pl.igorwumk.drivetracker.database.dao.TrackingSessionDao
import pl.igorwumk.drivetracker.database.entity.TrackingSession
import pl.igorwumk.drivetracker.service.TrackingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackingDetailActivity : AppCompatActivity() {
    private lateinit var toolbar: Toolbar
    private lateinit var mapView: MapView
    private lateinit var textViewDetails: TextView
    private lateinit var database: TrackingDatabase

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gpx+xml")
    ) { uri: Uri? ->
        uri?.let { persistGPXToURI(it) }
    }

    private lateinit var dao: TrackingSessionDao
    private var sessionId: Long = -1
    private lateinit var session: TrackingSession

    private var sessionStartTime: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking_detail)

        window.statusBarColor = ContextCompat.getColor(this, R.color.purple_500)

        dao = TrackingDatabase.getDatabase(this).sessionDao()

        toolbar = findViewById(R.id.tracking_detail_toolbar)
        mapView = findViewById(R.id.details_map_view)
        textViewDetails = findViewById(R.id.text_view_details)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Database instance
        database = TrackingDatabase.getDatabase(this)

        // Get session ID from Intent extras
        sessionId = intent.getLongExtra("SESSION_ID", -1)
        if (sessionId == -1L) {
            finish() // Invalid session - exit
            return
        }

        // Load session details
        GlobalScope.launch(Dispatchers.IO) {
            session = dao.getSession(sessionId)!!
            val sessionWithSegments = database.sessionDao().getSessionWithSegments(sessionId)
            sessionWithSegments?.let { sessionData ->
                withContext(Dispatchers.Main) {
                    textViewDetails.text = getString(R.string.tracking_details, Date(sessionData.session.startTime), sessionData.session.totalDistance, sessionData.session.totalTime)
                    sessionStartTime = sessionData.session.startTime
                    drawSegmentsOnMap(sessionData.segments)
                }
            }
        }
    }

    // Inflate options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_tracking_detail, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_gpx -> {
                // Launch SAF create-document prompt
                val defaultName = "track_${unixMilisToTimestamp(sessionStartTime)}.gpx"
                createDocument.launch(defaultName)
                true
            }
            R.id.action_delete -> {
                confirmDelete()
                true
            }
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun persistGPXToURI(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Generate GPX text
                val gpxText = TrackingService.generateGPX(this@TrackingDetailActivity, sessionId)
                // Type GPX contents
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(gpxText.toByteArray(Charsets.UTF_8))
                }
                // Notify user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrackingDetailActivity, R.string.export_success_zip, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TrackingDetailActivity, getString(R.string.export_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_delete_session)
            .setMessage(R.string.confirm_user_choice)
            .setNegativeButton(R.string.modal_no, null)
            .setPositiveButton(R.string.modal_yes) { _, _ -> applyDeletion() }
            .show()
    }

    private fun applyDeletion() = lifecycleScope.launch(Dispatchers.IO) {
        val newStatus = if (session.status == "syncPending") {
            "deleted"
        } else {
            "deletePending"
        }
        dao.updateSyncStatus(sessionId, newStatus)
        withContext(Dispatchers.Main) {
            Toast.makeText(this@TrackingDetailActivity, R.string.session_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun drawSegmentsOnMap(segments: List<TrackingSegmentWithPoints>) {
        // Use OSMDroid overlays
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(false)
        mapView.setMultiTouchControls(true)
        val mapController = mapView.controller
        mapController.setZoom(15.0)
        // Center map on first point
        segments.firstOrNull()?.points?.firstOrNull()?.let { firstPoint ->
            mapController.setCenter(GeoPoint(firstPoint.latitude, firstPoint.longitude))
        }
        mapView.overlays.clear()
        segments.forEach { segmentWithPoints ->
            val geoPoints = segmentWithPoints.points.map { point ->
                GeoPoint(point.latitude, point.longitude)
            }
            val polyline = Polyline().apply {
                setPoints(geoPoints)
                color = Color.RED
                width = 5.0f
            }
            mapView.overlays.add(polyline)
        }
        mapView.invalidate()
    }
}

fun unixMilisToTimestamp(milis: Long): String {
    val date = Date(milis)
    val formatter = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.getDefault())
    return formatter.format(date)
}