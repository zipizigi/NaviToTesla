package me.zipi.navitotesla.db

import androidx.room.DeleteTable
import androidx.room.RenameColumn
import androidx.room.migration.AutoMigrationSpec

@DeleteTable(tableName = "destination_send_cache")
@RenameColumn(tableName = "poi_address", fromColumnName = "address", toColumnName = "roadAddress")
class AppDatabaseAutoMigration7To8 : AutoMigrationSpec
