package pl.igorwumk.drivetracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_sessions")
data class TrackingSession(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val startTime: Long,        // In miliseconds
    val totalDistance: Double,  // In meters
    val totalTime: Long,        // In seconds
    val timezone: String,
    val locale: String,
    val isSynced: Boolean = false // New sessions not synced by default
)
