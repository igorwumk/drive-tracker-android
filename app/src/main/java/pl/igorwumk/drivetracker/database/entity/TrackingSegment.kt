package pl.igorwumk.drivetracker.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_segments",
    foreignKeys = [ForeignKey(
        entity = TrackingSession::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["segmentId"], name = "idx_segments_segment"),
        Index(value = ["sessionId"], name = "idx_segments_session"),
        Index(value = ["segmentOrder"], name = "idx_segments_order")
    ]
)
data class TrackingSegment(
    @PrimaryKey(autoGenerate = true)
    val segmentId: Long = 0,
    val sessionId: Long,
    val segmentOrder: Int // For sorting
)
