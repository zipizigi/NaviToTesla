package me.zipi.navitotesla.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM poi_address WHERE registered IS NULL OR registered = 0")
            db.execSQL("UPDATE poi_address SET sentMode = 'ROAD' WHERE registered = 1")
        }
    }

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE poi_address ADD COLUMN searchable INTEGER")
            db.execSQL("UPDATE poi_address SET sentMode = NULL WHERE registered = 0")
        }
    }

val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 동일 poi 에 (NULL) 과 ('') row 공존 시 UPDATE unique index 충돌 방지 — NULL row 먼저 삭제.
            db.execSQL(
                "DELETE FROM poi_address WHERE packageName IS NULL " +
                    "AND poi IN (SELECT poi FROM poi_address WHERE packageName = '')",
            )
            db.execSQL("UPDATE poi_address SET packageName = '' WHERE packageName IS NULL")
        }
    }
