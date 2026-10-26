package com.decibell.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.decibell.util.AppConstants
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "eq_presets")
data class EqPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bandCount: Int,
    val isChannelJoined: Boolean,
    val leftGainsCsv: String,
    val rightGainsCsv: String,
    val preGainDb: Float,
    val balanceDb: Float,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "eq_frequency_configs")
data class EqConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bandCount: Int,
    val isDualChannel: Boolean,
    val leftCutoffsCsv: String,
    val rightCutoffsCsv: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface EqDao {
    @Query("SELECT * FROM eq_presets ORDER BY name ASC")
    fun observeAllPresets(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets ORDER BY name ASC")
    suspend fun getAllPresetsOnce(): List<EqPresetEntity>

    @Query("SELECT * FROM eq_presets WHERE id = :id LIMIT 1")
    suspend fun getPresetById(id: Long): EqPresetEntity?

    @Query("SELECT * FROM eq_presets WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): EqPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqPresetEntity): Long

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long): Int

    @Query("DELETE FROM eq_presets WHERE name = :name")
    suspend fun deletePresetByName(name: String): Int

    @Query("SELECT * FROM eq_frequency_configs ORDER BY bandCount ASC, name ASC")
    fun observeAllConfigs(): Flow<List<EqConfigEntity>>

    @Query("SELECT * FROM eq_frequency_configs ORDER BY bandCount ASC, name ASC")
    suspend fun getAllConfigsOnce(): List<EqConfigEntity>

    @Query("SELECT * FROM eq_frequency_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: Long): EqConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: EqConfigEntity): Long

    @Query("DELETE FROM eq_frequency_configs WHERE id = :id")
    suspend fun deleteConfigById(id: Long): Int
}

@Database(
    entities = [EqPresetEntity::class, EqConfigEntity::class],
    version = 1,
    exportSchema = false
)
abstract class DecibellDatabase : RoomDatabase() {
    abstract fun eqDao(): EqDao

    companion object {
        @Volatile
        private var instance: DecibellDatabase? = null

        fun getInstance(context: Context): DecibellDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DecibellDatabase::class.java,
                    AppConstants.DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
