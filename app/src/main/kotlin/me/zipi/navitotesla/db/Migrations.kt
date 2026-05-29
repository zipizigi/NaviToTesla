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

val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 즐겨찾기(registered=1) 등록 시 user input trim 누락으로 poi 컬럼에 trailing/leading space 가
            // 박힌 row 가 share lookup (strict =) 에서 매칭 실패. trim 후 동일 (poi, packageName) 이
            // 이미 존재하면 dirty row 폐기, 없으면 trim 적용.
            db.execSQL(
                "DELETE FROM poi_address WHERE registered = 1 AND poi <> TRIM(poi) AND EXISTS (" +
                    "SELECT 1 FROM poi_address other " +
                    "WHERE other.id <> poi_address.id " +
                    "AND other.poi = TRIM(poi_address.poi) " +
                    "AND other.packageName IS poi_address.packageName)",
            )
            db.execSQL("UPDATE poi_address SET poi = TRIM(poi) WHERE registered = 1 AND poi <> TRIM(poi)")
        }
    }

// 정책 변경: 즐겨찾기는 roadAddress 컬럼 하나만 사용. sentMode 컬럼 폐지.
//   - JIBUN/GPS 모드로 저장됐던 favorite 의 사용자 의도를 roadAddress 로 복원
//   - favorite 의 jibun/lat/lng 보조 컬럼 정리 (실제로 share 에서 안 쓰임)
//   - sentMode 컬럼 자체 제거 (SQLite ALTER DROP COLUMN 은 API 34+ 만 지원 — table rebuild)
val MIGRATION_14_15 =
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE poi_address SET roadAddress = jibunAddress " +
                    "WHERE registered = 1 AND sentMode = 'JIBUN' AND jibunAddress IS NOT NULL",
            )
            db.execSQL(
                "UPDATE poi_address SET roadAddress = latitude || ',' || longitude " +
                    "WHERE registered = 1 AND sentMode = 'GPS' " +
                    "AND latitude IS NOT NULL AND longitude IS NOT NULL",
            )
            db.execSQL(
                "UPDATE poi_address SET jibunAddress = NULL, latitude = NULL, longitude = NULL " +
                    "WHERE registered = 1",
            )

            // sentMode 컬럼 제거 — table rebuild.
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
                """.trimIndent(),
            )
            db.execSQL(
                "INSERT INTO poi_address_new (id, poi, packageName, roadAddress, jibunAddress, " +
                    "latitude, longitude, registered, isDuplicate, searchable, created, lastCheckedAt, lastUsedAt) " +
                    "SELECT id, poi, packageName, roadAddress, jibunAddress, " +
                    "latitude, longitude, registered, isDuplicate, searchable, created, lastCheckedAt, lastUsedAt " +
                    "FROM poi_address",
            )
            db.execSQL("DROP TABLE poi_address")
            db.execSQL("ALTER TABLE poi_address_new RENAME TO poi_address")
            db.execSQL("CREATE UNIQUE INDEX index_poi_address_poi_packageName ON poi_address(poi, packageName)")
        }
    }
