package pl.igorwumk.drivetracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pl.igorwumk.drivetracker.database.entity.TrackingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackingSessionAdapter(
    private val clickListener: (TrackingSession) -> Unit
) : ListAdapter<TrackingSession, TrackingSessionAdapter.SessionViewHolder>(
    TrackingSessionDiffCallback()
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_tracking_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = getItem(position)
        holder.bind(session, clickListener)
    }

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTextView: TextView = itemView.findViewById(R.id.text_date)
        private val distanceTextView: TextView = itemView.findViewById(R.id.text_distance)
        private val timeTextView: TextView = itemView.findViewById(R.id.text_time)

        fun bind(session: TrackingSession, clickListener: (TrackingSession) -> Unit) {
            // Format start date/time
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val dateString = dateFormat.format(Date(session.startTime))
            dateTextView.text = dateString

            // Format distance (assuming meters)
            distanceTextView.text = "Distance: ${String.format("%.2f", session.totalDistance)} m"

            // Format total time (assuming seconds)
            val hours = session.totalTime / 3600
            val minutes = (session.totalTime % 3600) / 60
            val seconds = session.totalTime % 60
            timeTextView.text = String.format("Time: %02d:%02d:%02d", hours, minutes, seconds)

            // Setup click listener
            itemView.setOnClickListener {
                clickListener(session)
            }
        }
    }
}