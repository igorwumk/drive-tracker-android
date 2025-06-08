package pl.igorwumk.drivetracker

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_points",
    foreignKeys = [ForeignKey(
        entity = TrackingSegment::class,
        parentColumns = ["segmentId"],
        childColumns = ["segmentId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class TrackingPoint(
    @PrimaryKey(autoGenerate = true)
    val pointId: Long = 0,
    val segmentId: Long,
    val pointOrder: Int,    // For ordering in segment
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long     // In miliseconds
)
