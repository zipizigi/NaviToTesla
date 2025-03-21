package me.zipi.navitotesla.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [PoiAddressEntity::class, ConditionEntity::class],
    version = 5,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
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

        /**
         * Build the database. [Builder.build] only sets up the database configuration and
         * creates a new instance of the database.
         * The SQLite database is only created when it's accessed for the first time.
         */
        private fun buildDatabase(appContext: Context): AppDatabase =
            Room
                .databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
                // .addMigrations(MIGRATION_2_3)
                .build()
    }
}
