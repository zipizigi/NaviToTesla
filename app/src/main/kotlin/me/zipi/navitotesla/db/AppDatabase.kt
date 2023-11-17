package me.zipi.navitotesla.db

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PoiAddressEntity::class, ConditionEntity::class],
    version = 4,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)]
)
@TypeConverters(DateConverter::class)
abstract class AppDatabase : RoomDatabase() {
    private val mIsDatabaseCreated = MutableLiveData<Boolean>()
    abstract fun poiAddressDao(): PoiAddressDao
    abstract fun conditionDao(): ConditionDao

    /**
     * Check whether the database already exists and expose it via [.getDatabaseCreated]
     */
    private fun updateDatabaseCreated(context: Context) {
        if (context.getDatabasePath(DATABASE_NAME).exists()) {
            setDatabaseCreated()
        }
    }

    private fun setDatabaseCreated() {
        mIsDatabaseCreated.postValue(true)
    }

    val databaseCreated: LiveData<Boolean>
        get() = mIsDatabaseCreated

    companion object {
        const val DATABASE_NAME = "data.sqlite"
        private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            if (instance == null) {
                synchronized(AppDatabase::class.java) {
                    if (instance == null) {
                        instance = buildDatabase(context!!.applicationContext)
                        instance!!.updateDatabaseCreated(context.applicationContext)
                    }
                }
            }
            return instance!!
        }

        /**
         * Build the database. [Builder.build] only sets up the database configuration and
         * creates a new instance of the database.
         * The SQLite database is only created when it's accessed for the first time.
         */
        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        getInstance(appContext).setDatabaseCreated()
                    }
                }) //.addMigrations(MIGRATION_2_3)
                .build()
        }
    }
}