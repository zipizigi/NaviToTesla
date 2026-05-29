package me.zipi.navitotesla.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class PoiAddressMigrationTest {
    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            listOf(AppDatabaseAutoMigration7To8()),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    @Throws(IOException::class)
    fun migrate7To8_dropsDestinationSendCache_andRenamesAddressColumn() {
        helper.createDatabase(testDbName, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO poi_address (poi, address, registered, packageName, created)
                VALUES ('home', 'old road', 1, 'pkg.a', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO poi_address (poi, address, registered, packageName, created)
                VALUES ('cafe', 'cafe road', 0, 'pkg.a', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO destination_send_cache (poi, sentAddress, sentAsJibun, packageName, created)
                VALUES ('home', 'sent road', 0, 'pkg.a', 1700000000000)
                """.trimIndent(),
            )
        }

        val db =
            helper.runMigrationsAndValidate(
                testDbName,
                8,
                true,
            )

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='destination_send_cache'").use {
            assertEquals(0, it.count)
        }

        db.query("SELECT poi, roadAddress, registered FROM poi_address ORDER BY poi").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals("cafe", cursor.getString(0))
            assertEquals("cafe road", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            cursor.moveToNext()
            assertEquals("home", cursor.getString(0))
            assertEquals("old road", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
        }

        db.query("SELECT jibunAddress, sentMode FROM poi_address").use { cursor ->
            while (cursor.moveToNext()) {
                assertTrue(cursor.isNull(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate8To9_deletesUnregisteredRows_andStampsSentModeForRegistered() {
        helper.createDatabase(testDbName, 8).use { db ->
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, isDuplicate, sentMode, created)
                VALUES ('home', 'pkg.a', 'home road', NULL, NULL, NULL, 1, NULL, NULL, 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, isDuplicate, sentMode, created)
                VALUES ('cafe', 'pkg.a', 'cafe road', NULL, NULL, NULL, 0, NULL, NULL, 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, isDuplicate, sentMode, created)
                VALUES ('temp', 'pkg.a', 'temp road', NULL, NULL, NULL, NULL, NULL, NULL, 1700000000000)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(testDbName, 9, true, MIGRATION_8_9)

        db.query("SELECT poi, sentMode FROM poi_address").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("home", cursor.getString(0))
            assertEquals("ROAD", cursor.getString(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_twoDirtyFavoritesWithSameTrimResult_doNotConflict() {
        // 같은 packageName 에 trim 후 같은 결과가 되는 dirty favorite 두 개 — UNIQUE 충돌 없이 마이그레이션 통과해야 함.
        helper.createDatabase(testDbName, 13).use { db ->
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, registered, sentMode, created)
                VALUES ('집 ', 'pkg.a', 'road A', 1, 'ROAD', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, registered, sentMode, created)
                VALUES ('집  ', 'pkg.a', 'road B', 1, 'ROAD', 1700000000001)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(testDbName, 14, true, MIGRATION_13_14)

        db.query("SELECT poi, roadAddress FROM poi_address WHERE registered = 1").use { cursor ->
            assertEquals(1, cursor.count) // 하나만 살아남음 (id 작은 쪽 = 'road A')
            cursor.moveToFirst()
            assertEquals("집", cursor.getString(0))
            assertEquals("road A", cursor.getString(1))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate13To14_trimsRegisteredPoiWhitespace_andDropsDirtyWhenCleanExists() {
        helper.createDatabase(testDbName, 13).use { db ->
            // 1) trailing space favorite, 같은 packageName 에 clean ver 없음 → TRIM 적용되어 살아남아야 함
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, registered, sentMode, created)
                VALUES ('home ', '', 'home road', 1, 'ROAD', 1700000000000)
                """.trimIndent(),
            )
            // 2) trailing space favorite, 같은 packageName 에 clean ver 이미 존재 → dirty 폐기
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, registered, sentMode, created)
                VALUES ('cafe ', 'pkg.a', 'cafe road dirty', 1, 'ROAD', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, registered, sentMode, created)
                VALUES ('cafe', 'pkg.a', 'cafe road clean', 1, 'ROAD', 1700000000001)
                """.trimIndent(),
            )
            // 3) registered=0 (cache) 에 공백 — 정책상 건드리지 않음
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, registered, sentMode, created)
                VALUES ('cache ', 'pkg.a', 'cache road', 0, NULL, 1700000000000)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(testDbName, 14, true, MIGRATION_13_14)

        db.query("SELECT poi, packageName, roadAddress, registered FROM poi_address ORDER BY poi, packageName").use { cursor ->
            assertEquals(3, cursor.count)
            cursor.moveToFirst()
            assertEquals("cache ", cursor.getString(0)) // registered=0 untouched
            assertEquals("pkg.a", cursor.getString(1))
            assertEquals(0, cursor.getInt(3))
            cursor.moveToNext()
            assertEquals("cafe", cursor.getString(0)) // clean 만 남고 dirty 폐기됨
            assertEquals("pkg.a", cursor.getString(1))
            assertEquals("cafe road clean", cursor.getString(2))
            cursor.moveToNext()
            assertEquals("home", cursor.getString(0)) // trailing space 제거됨
            assertEquals("", cursor.getString(1))
            assertEquals("home road", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate14To15_restoresFavoriteIntent_andDropsSentModeColumn() {
        helper.createDatabase(testDbName, 14).use { db ->
            // JIBUN 모드 favorite — roadAddress 컬럼이 jibunAddress 로 복원되어야
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, sentMode, created)
                VALUES ('jibun_fav', 'pkg.a', 'road val', 'jibun val', NULL, NULL, 1, 'JIBUN', 1700000000000)
                """.trimIndent(),
            )
            // GPS 모드 favorite — roadAddress 컬럼이 "lat,lng" 로 복원되어야
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, sentMode, created)
                VALUES ('gps_fav', 'pkg.a', 'road val', NULL, '37.5', '127.0', 1, 'GPS', 1700000000000)
                """.trimIndent(),
            )
            // ROAD 모드 favorite — 그대로 유지
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, sentMode, created)
                VALUES ('road_fav', 'pkg.a', 'road val', 'jibun val', NULL, NULL, 1, 'ROAD', 1700000000000)
                """.trimIndent(),
            )
            // registered=0 cache — favorite 정책 무관, jibun/lat/lng 그대로 보존되어야
            db.execSQL(
                """
                INSERT INTO poi_address (poi, packageName, roadAddress, jibunAddress, latitude, longitude, registered, sentMode, created)
                VALUES ('cache', 'pkg.a', 'cache road', 'cache jibun', '36.0', '128.0', 0, NULL, 1700000000000)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(testDbName, 15, true, MIGRATION_14_15)

        // sentMode 컬럼이 schema 에서 사라졌어야 함
        db.query("PRAGMA table_info(poi_address)").use { cursor ->
            val cols = mutableListOf<String>()
            while (cursor.moveToNext()) {
                cols += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue("sentMode 컬럼이 남아있음", "sentMode" !in cols)
        }

        db.query(
            "SELECT poi, roadAddress, jibunAddress, latitude, longitude, registered FROM poi_address ORDER BY poi",
        ).use { cursor ->
            assertEquals(4, cursor.count)

            cursor.moveToNext() // cache
            assertEquals("cache", cursor.getString(0))
            assertEquals("cache road", cursor.getString(1))
            assertEquals("cache jibun", cursor.getString(2))
            assertEquals("36.0", cursor.getString(3))
            assertEquals("128.0", cursor.getString(4))

            cursor.moveToNext() // gps_fav
            assertEquals("gps_fav", cursor.getString(0))
            assertEquals("37.5,127.0", cursor.getString(1)) // road = lat,lng
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))

            cursor.moveToNext() // jibun_fav
            assertEquals("jibun_fav", cursor.getString(0))
            assertEquals("jibun val", cursor.getString(1)) // road = jibun
            assertTrue(cursor.isNull(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))

            cursor.moveToNext() // road_fav
            assertEquals("road_fav", cursor.getString(0))
            assertEquals("road val", cursor.getString(1)) // road 유지
            assertTrue(cursor.isNull(2))
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate7To9_chained_preservesRegisteredOnly_andStampsSentMode() {
        helper.createDatabase(testDbName, 7).use { db ->
            db.execSQL(
                """
                INSERT INTO poi_address (poi, address, registered, packageName, created)
                VALUES ('home', 'old road', 1, 'pkg.a', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO poi_address (poi, address, registered, packageName, created)
                VALUES ('cafe', 'cafe road', 0, 'pkg.a', 1700000000000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO destination_send_cache (poi, sentAddress, sentAsJibun, packageName, created)
                VALUES ('home', 'sent road', 0, 'pkg.a', 1700000000000)
                """.trimIndent(),
            )
        }

        helper.runMigrationsAndValidate(testDbName, 8, true)
        val db = helper.runMigrationsAndValidate(testDbName, 9, true, MIGRATION_8_9)

        db.query("SELECT poi, roadAddress, jibunAddress, sentMode FROM poi_address").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("home", cursor.getString(0))
            assertEquals("old road", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals("ROAD", cursor.getString(3))
        }

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='destination_send_cache'").use {
            assertEquals(0, it.count)
        }
    }
}
