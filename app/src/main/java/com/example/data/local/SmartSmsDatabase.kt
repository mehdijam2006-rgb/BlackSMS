package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MessageEntity::class, FilterRuleEntity::class, ListEntryEntity::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SmartSmsDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun filterRuleDao(): FilterRuleDao
    abstract fun listEntryDao(): ListEntryDao

    companion object {
        @Volatile
        private var INSTANCE: SmartSmsDatabase? = null

        fun getInstance(context: Context): SmartSmsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SmartSmsDatabase::class.java,
                    "black_sms_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
