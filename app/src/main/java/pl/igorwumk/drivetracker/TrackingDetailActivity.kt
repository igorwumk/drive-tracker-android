package pl.igorwumk.drivetracker

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.util.Date

class TrackingDetailActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var textViewDetails: TextView
    private lateinit var database: TrackingDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking_detail)

        mapView = findViewById(R.id.details_map_view)
        textViewDetails = findViewById(R.id.text_view_details)

        // Database instance
        database = TrackingDatabase.getDatabase(this)

        // Get session ID from Intent extras
        val sessionId = intent.getLongExtra("SESSION_ID", -1)
        if (sessionId == -1L) {
            finish() // Invalid session - exit
            return
        }

        // Load session details
        GlobalScope.launch(Dispatchers.IO) {
            val sessionWithSegments = database.sessionDao().getSessionWithSegments(sessionId)
            sessionWithSegments?.let { sessionData ->
                withContext(Dispatchers.Main) {
                    textViewDetails.text =
                        "Started: ${Date(sessionData.session.startTime)}\n" +
                        "Distance: ${sessionData.session.totalDistance} m\n" +
                        "Time: ${sessionData.session.totalTime} sec"
                    drawSegmentsOnMap(sessionData.segments)
                }
            }
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