package pl.igorwumk.drivetracker

import androidx.room.Embedded
import androidx.room.Relation
import pl.igorwumk.drivetracker.database.entity.TrackingPoint
import pl.igorwumk.drivetracker.database.entity.TrackingSegment
import pl.igorwumk.drivetracker.database.entity.TrackingSession

data class TrackingSegmentWithPoints(
    @Embedded val segment: TrackingSegment,
    @Relation(
        parentColumn = "segmentId",
        entityColumn = "segmentId"
    )
    val points: List<TrackingPoint>
)

data class TrackingSessionWithSegments(
    @Embedded val session: TrackingSession,
    @Relation(
        parentColumn = "sessionId",
        entity = TrackingSegment::class,
        entityColumn = "sessionId"
    )
    val segments: List<TrackingSegmentWithPoints>
)
