package pl.igorwumk.drivetracker

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TrackingSession::class, TrackingSegment::class, TrackingPoint::class],
    version = 2,
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
                ).addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    @SuppressLint("Range")
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) Turn off FK constraints while we rebuild the table
        db.execSQL("PRAGMA foreign_keys=OFF;")

        // 2) Create the new table with all old columns + new `status` (no isSynced)
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS tracking_sessions_new (
            sessionId   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            startTime   INTEGER NOT NULL,
            totalDistance REAL   NOT NULL,
            totalTime   INTEGER NOT NULL,
            timezone    TEXT    NOT NULL,
            locale      TEXT    NOT NULL,
            status      TEXT    NOT NULL DEFAULT 'syncPending'
          );
        """.trimIndent())

        // 3) Copy & transform the data
        db.execSQL("""
          INSERT INTO tracking_sessions_new
            (sessionId, startTime, totalDistance, totalTime, timezone, locale, status)
          SELECT
            ts.sessionId,
            ts.startTime,
            ts.totalDistance,
            CASE
              WHEN ts.totalTime = 0 THEN (
                -- recompute from your points & segments
                SELECT MAX(p.timestamp) - MIN(p.timestamp)
                FROM tracking_points AS p
                JOIN tracking_segments AS s
                  ON p.segmentId = s.segmentId
                WHERE s.sessionId = ts.sessionId
              ) / 1000 -- milliseconds to seconds
              ELSE ts.totalTime
            END AS totalTime,
            ts.timezone,
            ts.locale,
            CASE
              WHEN ts.isSynced = 1 THEN 'synced'
              ELSE 'syncPending'
            END AS status
          FROM tracking_sessions AS ts;
        """.trimIndent())

        // 4) Log a warning for every formerly‐synced session (there should be none at time of this migration)
        val cursor = db.query("SELECT sessionId FROM tracking_sessions WHERE isSynced = 1;")
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndex("sessionId"))
            Log.w("MIGRATION_1_2", "Session $id was isSynced=true → status='synced'")
        }
        cursor.close()

        // 5) Drop the old table and rename the new one
        db.execSQL("DROP TABLE tracking_sessions;")
        db.execSQL("ALTER TABLE tracking_sessions_new RENAME TO tracking_sessions;")

        // 6) Re‐enable foreign-key constraints
        db.execSQL("PRAGMA foreign_keys=ON;")
    }
}