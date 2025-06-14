package pl.igorwumk.drivetracker.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import pl.igorwumk.drivetracker.TrackingSessionWithSegments
import pl.igorwumk.drivetracker.database.entity.TrackingSession

@Dao
interface TrackingSessionDao {

    @Insert
    suspend fun insertSession(session: TrackingSession): Long

    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: Long): TrackingSession?

    @Query("SELECT * FROM tracking_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<TrackingSession>

    @Transaction
    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionWithSegments(sessionId: Long): TrackingSessionWithSegments?

    @Query("UPDATE tracking_sessions SET status = :newStatus WHERE sessionId = :sessionId")
    suspend fun updateSyncStatus(sessionId: Long, newStatus: String)

    // Convenience methods
    suspend fun markSynced(sessionId: Long) = updateSyncStatus(sessionId, "synced")
    suspend fun markSyncPending(sessionId: Long) = updateSyncStatus(sessionId, "syncPending")
    suspend fun markDeletePending(sessionId: Long) = updateSyncStatus(sessionId, "deletePending")
    suspend fun markDeleted(sessionId: Long) = updateSyncStatus(sessionId, "deleted")
}
