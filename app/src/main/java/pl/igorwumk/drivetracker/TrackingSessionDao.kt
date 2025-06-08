package pl.igorwumk.drivetracker

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TrackingSessionDao {

    @Insert
    suspend fun insertSession(session: TrackingSession): Long

    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: Long): TrackingSession?

    @Transaction
    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionWithSegments(sessionId: Long): TrackingSessionWithSegments?

    @Query("UPDATE tracking_sessions SET isSynced = :syncStatus WHERE sessionId = :sessionId")
    suspend fun updateSyncStatus(sessionId: Long, syncStatus: Boolean)
}
