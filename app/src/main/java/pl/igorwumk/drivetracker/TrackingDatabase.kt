package pl.igorwumk.drivetracker

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackingSession::class, TrackingSegment::class, TrackingPoint::class],
    version = 1,
    exportSchema = true
)
abstract class TrackingDatabase : RoomDatabase() {

    abstract fun sessionDao(): TrackingSessionDao
    abstract fun segmentDao(): TrackingSegmentDao
    abstract fun pointDao(): TrackingPointDao

    companion object {
        @Volatile
        private var INSTANCE: TrackingDatabase? = null

        fun getDatabase(context: Context): TrackingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrackingDatabase::class.java,
                    "tracking_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
