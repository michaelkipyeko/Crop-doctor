package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisDao {
    @Query("SELECT * FROM diagnosis_records ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<DiagnosisRecordEntity>>

    @Query("SELECT * FROM diagnosis_records WHERE id = :id")
    fun observeById(id: Long): Flow<DiagnosisRecordEntity?>

    @Query("SELECT * FROM diagnosis_records WHERE id = :id")
    suspend fun getById(id: Long): DiagnosisRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DiagnosisRecordEntity): Long

    @Query("UPDATE diagnosis_records SET stepsJoined = :stepsJoined WHERE id = :id")
    suspend fun updateSteps(id: Long, stepsJoined: String)

    @Query("UPDATE diagnosis_records SET isResolved = :resolved WHERE id = :id")
    suspend fun updateResolved(id: Long, resolved: Boolean)

    @Query("DELETE FROM diagnosis_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
