package pl.igorwumk.drivetracker

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface TrackingSegmentDao {
    @Insert
    suspend fun insertSegment(segment: TrackingSegment): Long
}
