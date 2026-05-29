package me.zipi.navitotesla.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PoiAddressEntity::class, ConditionEntity::class],
    version = 15,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = AppDatabaseAutoMigration7To8::class),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
    ],
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun poiAddressDao(): PoiAddressDao

    abstract fun conditionDao(): ConditionDao

    companion object {
        private const val DATABASE_NAME = "data.sqlite"
        private lateinit var instance: AppDatabase

        fun initialize(applicationContext: Context) {
            if (!this::instance.isInitialized) {
                synchronized(AppDatabase::class) {
                    instance = buildDatabase(applicationContext.applicationContext)
                }
            }
        }

        fun getInstance(): AppDatabase = instance

        private fun buildDatabase(appContext: Context): AppDatabase =
            Room
                .databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                .build()
    }
}
