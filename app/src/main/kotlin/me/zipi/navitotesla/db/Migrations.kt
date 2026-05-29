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

// v1~v5 시절 등록된 favorite 가 v6 마이그레이션 시 packageName=NULL 로 채워져 매칭 누락되는 회귀(1.102) 정규화.
val MIGRATION_12_13 =
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE poi_address SET packageName = '' WHERE packageName IS NULL")
        }
    }
