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
