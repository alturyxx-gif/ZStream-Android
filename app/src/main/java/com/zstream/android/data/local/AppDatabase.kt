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
import com.zstream.android.data.local.dao.LocalFileProgressDao
import com.zstream.android.data.local.dao.LocalLibraryDao
import com.zstream.android.data.local.dao.ProgressDao
import com.zstream.android.data.local.dao.SkipSegmentDao
import com.zstream.android.data.local.entity.BookmarkEntity
import com.zstream.android.data.local.entity.DownloadEntity
import com.zstream.android.data.local.entity.LocalFileProgressEntity
import com.zstream.android.data.local.entity.LocalLibraryFolderEntity
import com.zstream.android.data.local.entity.LocalMediaEntity
import com.zstream.android.data.local.entity.ProgressEntity
import com.zstream.android.data.local.entity.SkipSegmentEntity

@Database(
    entities = [
        ProgressEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class,
        LocalLibraryFolderEntity::class,
        LocalMediaEntity::class,
        LocalFileProgressEntity::class,
        SkipSegmentEntity::class,
    ],

    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun localLibraryDao(): LocalLibraryDao
    abstract fun localFileProgressDao(): LocalFileProgressDao
    abstract fun skipSegmentDao(): SkipSegmentDao

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

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN contentFingerprint TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_file_progress (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        posterPath TEXT,
                        thumbnailPath TEXT,
                        watched INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skip_segments (
                        id TEXT PRIMARY KEY NOT NULL,
                        mediaKey TEXT NOT NULL,
                        segmentType TEXT NOT NULL,
                        startMs INTEGER,
                        endMs INTEGER,
                        source TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_skip_segments_mediaKey ON skip_segments(mediaKey)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_media ADD COLUMN fingerprint TEXT")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN segDone INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN segTotal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN speedBps INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN bytesDownloaded INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN estimatedTotalBytes INTEGER")
                db.execSQL("ALTER TABLE downloads ADD COLUMN streamUrl TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN streamType TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN audioStreamUrl TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN audioLanguage TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN headersJson TEXT")
                db.execSQL("ALTER TABLE downloads ADD COLUMN captionsJson TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN remuxDone INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloads ADD COLUMN remuxTotal INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zstream.db"
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
