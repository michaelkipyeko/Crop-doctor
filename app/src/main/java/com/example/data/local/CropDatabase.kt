package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DiagnosisRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CropDatabase : RoomDatabase() {
    abstract fun diagnosisDao(): DiagnosisDao

    companion object {
        @Volatile
        private var INSTANCE: CropDatabase? = null

        fun getInstance(context: Context): CropDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CropDatabase::class.java,
                    "crop_doctor.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
