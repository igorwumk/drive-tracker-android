package pl.igorwumk.drivetracker.database.dao

import androidx.room.Dao
import androidx.room.Insert
import pl.igorwumk.drivetracker.database.entity.TrackingSegment

@Dao
interface TrackingSegmentDao {
    @Insert
    suspend fun insertSegment(segment: TrackingSegment): Long
}
