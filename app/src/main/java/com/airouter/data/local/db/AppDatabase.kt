package com.airouter.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.airouter.data.local.db.dao.MessageDao
import com.airouter.data.local.db.dao.ProviderConfigDao
import com.airouter.data.local.db.dao.SessionDao
import com.airouter.data.local.db.entity.MessageEntity
import com.airouter.data.local.db.entity.ProviderConfigEntity
import com.airouter.data.local.db.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ProviderConfigEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun providerConfigDao(): ProviderConfigDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN attachments TEXT DEFAULT NULL")
            }
        }

        /**
         * v2 → v3: 清除 attachments 列中包含 base64Data 的旧数据（导致 CursorWindow 溢出闪退）。
         * 新版只存 localPath，不再包含大 base64 字符串。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 将含有 base64Data 字段的旧 attachments JSON 清空
                db.execSQL("UPDATE messages SET attachments = NULL WHERE attachments IS NOT NULL AND attachments LIKE '%base64Data%'")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE provider_configs ADD COLUMN fetchedModelsJson TEXT DEFAULT NULL")
            }
        }
    }
}
