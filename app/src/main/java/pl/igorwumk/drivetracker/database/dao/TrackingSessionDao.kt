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

    @Query("SELECT * FROM tracking_sessions WHERE status = :status")
    suspend fun getAllByStatus(status: String): List<TrackingSession>

    @Transaction
    @Query("SELECT * FROM tracking_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionWithSegments(sessionId: Long): TrackingSessionWithSegments?

    @Query("UPDATE tracking_sessions SET status = :newStatus WHERE sessionId = :sessionId")
    suspend fun updateSyncStatus(sessionId: Long, newStatus: String)

    @Query("UPDATE tracking_sessions SET status = 'desynced' WHERE status = 'synced'")
    suspend fun markAllSyncedAsDesynced()

    @Query("DELETE FROM tracking_sessions WHERE status = :status")
    suspend fun deleteByStatus(status: String): Int

    @Query("""
        SELECT * FROM tracking_sessions
        WHERE startTime = :startTime
        AND totalDistance = :totalDistance
        AND totalTime = :totalTime
        AND timezone = :timezone
    """)
    suspend fun findByUniqueness(startTime: Long, totalDistance: Double, totalTime: Long, timezone: String): TrackingSession?

    // Convenience methods
    suspend fun markSynced(sessionId: Long) = updateSyncStatus(sessionId, "synced")
    suspend fun markSyncPending(sessionId: Long) = updateSyncStatus(sessionId, "syncPending")
    suspend fun markDeletePending(sessionId: Long) = updateSyncStatus(sessionId, "deletePending")
    suspend fun markDeleted(sessionId: Long) = updateSyncStatus(sessionId, "deleted")
}
