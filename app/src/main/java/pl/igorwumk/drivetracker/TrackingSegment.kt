package pl.igorwumk.drivetracker

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_segments",
    foreignKeys = [ForeignKey(
        entity = TrackingSession::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TrackingSegment(
    @PrimaryKey(autoGenerate = true)
    val segmentId: Long = 0,
    val sessionId: Long,
    val segmentOrder: Int // For sorting
)
