package pl.igorwumk.drivetracker

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : BaseDrawerActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TrackingSessionAdapter
    private lateinit var database: TrackingDatabase

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
        GlobalScope.launch(Dispatchers.IO) {
            val sessions = database.sessionDao().getAllSessions()
            withContext(Dispatchers.Main) {
                adapter.submitList(sessions)
            }
        }
    }
}