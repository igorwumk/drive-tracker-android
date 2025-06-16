package pl.igorwumk.drivetracker.api

// Shallow session list item
data class TrackingSessionDto(
    val id: Long,
    val startTime: Long,
    val totalDistance: Double,
    val totalTime: Long,
    val timezone: String,
    val locale: String
)

// Full session with nested segments and points
data class TrackingPointDto(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val pointOrder: Int
)
data class TrackingSegmentDto(
    val id: Long,
    val segmentOrder: Int,
    val points: List<TrackingPointDto>
)
data class FullTrackingSessionDto(
    val id: Long,
    val startTime: Long,
    val totalDistance: Double,
    val totalTime: Long,
    val timezone: String,
    val locale: String,
    val segments: List<TrackingSegmentDto>
)

// Upload session - omit id and status, send nested segments and points
data class TrackingPointUploadDto(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val pointOrder: Int
)
data class TrackingSegmentUploadDto(
    val segmentOrder: Int,
    val points: List<TrackingPointUploadDto>
)
data class TrackingSessionUploadDto(
    val startTime: Long,
    val totalDistance: Double,
    val totalTime: Long,
    val timezone: String,
    val locale: String,
    val segments: List<TrackingSegmentUploadDto>
)
