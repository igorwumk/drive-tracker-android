package pl.igorwumk.drivetracker.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracking_points",
    foreignKeys = [ForeignKey(
        entity = TrackingSegment::class,
        parentColumns = ["segmentId"],
        childColumns = ["segmentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["pointId"], name = "idx_points_point"),
        Index(value = ["segmentId"], name = "idx_points_segment"),
        Index(value = ["pointOrder"], name = "idx_points_order")
    ]
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
