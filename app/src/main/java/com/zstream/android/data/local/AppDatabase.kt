package com.zstream.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zstream.android.data.local.dao.BookmarkDao
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.ProgressEntity

@Database(
    entities = [
        ProgressEntity::class,
        BookmarkEntity::class,
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zstream.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
