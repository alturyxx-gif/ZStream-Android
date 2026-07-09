package com.zstream.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zstream.android.data.local.dao.BookmarkDao
import com.zstream.android.data.local.dao.DownloadDao
import com.zstream.android.data.local.dao.LocalLibraryDao
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import com.zstream.android.data.local.entity.ProgressEntity

@Database(
    entities = [
        ProgressEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class,
        LocalLibraryFolderEntity::class,
        LocalMediaEntity::class,
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun localLibraryDao(): LocalLibraryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_library_folders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        treeUri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        lastScanAt INTEGER,
                        lastScanError TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_media (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        folderId INTEGER NOT NULL,
                        documentUri TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        relativePath TEXT NOT NULL,
                        size INTEGER,
                        durationMs INTEGER,
                        modifiedAt INTEGER,
                        groupTitle TEXT NOT NULL,
                        mediaKind TEXT NOT NULL,
                        season INTEGER,
                        episode INTEGER,
                        groupKey TEXT NOT NULL DEFAULT '',
                        matchSource TEXT NOT NULL DEFAULT 'legacy',
                        tmdbId TEXT,
                        tmdbType TEXT,
                        posterPath TEXT,
                        thumbnailPath TEXT,
                        metadataTitle TEXT,
                        FOREIGN KEY(folderId) REFERENCES local_library_folders(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_media_folderId ON local_media(folderId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_media ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_media ADD COLUMN matchSource TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("ALTER TABLE local_media ADD COLUMN tmdbId TEXT")
                db.execSQL("ALTER TABLE local_media ADD COLUMN tmdbType TEXT")
                db.execSQL("ALTER TABLE local_media ADD COLUMN posterPath TEXT")
                db.execSQL("ALTER TABLE local_media ADD COLUMN thumbnailPath TEXT")
                db.execSQL("ALTER TABLE local_media ADD COLUMN metadataTitle TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zstream.db"
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
