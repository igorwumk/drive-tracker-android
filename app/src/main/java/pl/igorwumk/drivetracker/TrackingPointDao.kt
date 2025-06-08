package pl.igorwumk.drivetracker

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface TrackingPointDao {
    @Insert
    suspend fun insertPoints(points: List<TrackingPoint>)
}
