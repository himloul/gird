package org.foss.gird

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeofenceDao {
    @Query("SELECT * FROM geofences")
    fun getAll(): Flow<List<Geofence>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(geofence: Geofence)

    @Delete
    suspend fun delete(geofence: Geofence)

    @Query("UPDATE geofences SET lastState = :state WHERE id = :id")
    suspend fun updateState(id: String, state: GeofenceState)

    @Query("UPDATE geofences SET isActive = :active WHERE id = :id")
    suspend fun toggleActive(id: String, active: Boolean)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("UPDATE tasks SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun updateCompletion(id: String, completed: Boolean, completedAt: Long?)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM geofence_events ORDER BY timestamp DESC LIMIT 200")
    fun getAll(): Flow<List<GeofenceEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GeofenceEvent)

    @Query("DELETE FROM geofence_events")
    suspend fun deleteAll()
}

@Database(entities = [Geofence::class, Task::class, GeofenceEvent::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun geofenceDao(): GeofenceDao
    abstract fun taskDao(): TaskDao
    abstract fun eventDao(): EventDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gird_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
