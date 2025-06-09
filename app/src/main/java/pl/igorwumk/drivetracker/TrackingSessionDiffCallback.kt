package pl.igorwumk.drivetracker

import androidx.recyclerview.widget.DiffUtil

class TrackingSessionDiffCallback: DiffUtil.ItemCallback<TrackingSession>() {
    override fun areItemsTheSame(oldItem: TrackingSession, newItem: TrackingSession): Boolean {
        // Compare unique IDs
        return oldItem.sessionId == newItem.sessionId
    }

    override fun areContentsTheSame(oldItem: TrackingSession, newItem: TrackingSession): Boolean {
        // Use data auto equals to check all fields
        return oldItem == newItem
    }
}