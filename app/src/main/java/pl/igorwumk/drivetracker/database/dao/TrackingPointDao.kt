package pl.igorwumk.drivetracker.database.dao

import androidx.room.Dao
import androidx.room.Insert
import pl.igorwumk.drivetracker.database.entity.TrackingPoint

@Dao
interface TrackingPointDao {
    @Insert
    suspend fun insertPoints(points: List<TrackingPoint>)
}
