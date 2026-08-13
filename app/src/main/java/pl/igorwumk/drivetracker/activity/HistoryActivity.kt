package pl.igorwumk.drivetracker.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.igorwumk.drivetracker.R
import pl.igorwumk.drivetracker.database.TrackingDatabase
import pl.igorwumk.drivetracker.TrackingSessionAdapter
import pl.igorwumk.drivetracker.service.TrackingService
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HistoryActivity : BaseDrawerActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrackingSessionAdapter
    private lateinit var database: TrackingDatabase

    private val createZipExport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { saveZipToUri(it) }
    }

    override fun getLayoutResourceId(): Int {
        return R.layout.activity_with_drawer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //setContentView(R.layout.activity_history)
        // Inflate the view
        layoutInflater.inflate(R.layout.activity_history, findViewById(R.id.content_frame), true)

        recyclerView = findViewById(R.id.recycler_view_sessions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TrackingSessionAdapter { session ->
            // Open detail screen when tapped
            val intent = Intent(this, TrackingDetailActivity::class.java)
            intent.putExtra("SESSION_ID", session.sessionId)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        // Get database instance
        database = TrackingDatabase.getDatabase(this)

        // Load sessions from database
        database.sessionDao().getActiveSessions().observe(this) { list ->
            adapter.submitList(list)
        }

    }

    // Inflate options menu
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_all -> {
                // Launch SAF create-document prompt
                val defaultName = "drive_tracker_export.zip"
                createZipExport.launch(defaultName)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveZipToUri(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val zipOutputStream = ByteArrayOutputStream()
                ZipOutputStream(zipOutputStream).use { zipSdk ->
                    for (item in adapter.currentList) {
                        val gpxText = TrackingService.generateGPX(this@HistoryActivity, item.sessionId)
                        val entry = ZipEntry("track_${unixMilisToTimestamp(item.startTime)}.gpx")
                        zipSdk.putNextEntry(entry)
                        zipSdk.write(gpxText.toByteArray(Charsets.UTF_8))
                        zipSdk.closeEntry()
                    }
                }
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(zipOutputStream.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryActivity, R.string.export_success_zip, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistoryActivity, getString(R.string.export_failed, e.localizedMessage), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}