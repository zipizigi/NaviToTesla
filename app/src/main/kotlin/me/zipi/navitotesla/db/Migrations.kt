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
            db.execSQL(
                """
                DELETE FROM poi_address WHERE packageName IS NULL
                AND poi IN (SELECT poi FROM poi_address WHERE packageName = '')
                """,
            )
            db.execSQL(
                """
                DELETE FROM poi_address WHERE packageName IS NULL
                AND id NOT IN (SELECT MIN(id) FROM poi_address WHERE packageName IS NULL GROUP BY poi)
                """,
            )
            db.execSQL("UPDATE poi_address SET packageName = '' WHERE packageName IS NULL")
        }
    }

val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM poi_address WHERE EXISTS (
                    SELECT 1 FROM poi_address other
                    WHERE other.id <> poi_address.id
                      AND TRIM(other.poi) = TRIM(poi_address.poi)
                      AND other.packageName IS poi_address.packageName
                      AND (
                          LENGTH(other.poi) < LENGTH(poi_address.poi)
                          OR (LENGTH(other.poi) = LENGTH(poi_address.poi) AND other.id < poi_address.id)
                      )
                )
                """,
            )
            db.execSQL("UPDATE poi_address SET poi = TRIM(poi) WHERE poi <> TRIM(poi)")
        }
    }

val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                UPDATE poi_address SET roadAddress = jibunAddress
                WHERE registered = 1 AND sentMode = 'JIBUN' AND jibunAddress IS NOT NULL
                """,
            )
            db.execSQL(
                """
                UPDATE poi_address SET roadAddress = latitude || ',' || longitude
                WHERE registered = 1 AND sentMode = 'GPS'
                  AND latitude IS NOT NULL AND longitude IS NOT NULL
                """,
            )
            db.execSQL(
                """
                UPDATE poi_address SET jibunAddress = NULL, latitude = NULL, longitude = NULL
                WHERE registered = 1
                """,
            )

            db.execSQL("DROP TABLE IF EXISTS poi_address_new")
            db.execSQL(
                """
                CREATE TABLE poi_address_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    poi TEXT NOT NULL,
                    packageName TEXT,
                    roadAddress TEXT,
                    jibunAddress TEXT,
                    latitude TEXT,
                    longitude TEXT,
                    registered INTEGER,
                    isDuplicate INTEGER,
                    searchable INTEGER,
                    created INTEGER,
                    lastCheckedAt INTEGER,
                    lastUsedAt INTEGER
                )
                """,
            )
            db.execSQL(
                """
                INSERT INTO poi_address_new (id, poi, packageName, roadAddress, jibunAddress,
                    latitude, longitude, registered, isDuplicate, searchable, created, lastCheckedAt, lastUsedAt)
                SELECT id, poi, packageName, roadAddress, jibunAddress,
                    latitude, longitude, registered, isDuplicate, searchable, created, lastCheckedAt, lastUsedAt
                FROM poi_address
                """,
            )
            db.execSQL("DROP TABLE poi_address")
            db.execSQL("ALTER TABLE poi_address_new RENAME TO poi_address")
            db.execSQL("CREATE UNIQUE INDEX index_poi_address_poi_packageName ON poi_address(poi, packageName)")
        }
    }
