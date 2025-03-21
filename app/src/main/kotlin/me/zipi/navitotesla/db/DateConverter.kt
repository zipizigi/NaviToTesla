package me.zipi.navitotesla.db

import androidx.room.TypeConverter
import java.util.Date

class DateConverter {
    @TypeConverter
    fun toDate(timestamp: Long?): Date? = if (timestamp == null) null else Date(timestamp)

    @TypeConverter
    fun toTimestamp(date: Date?): Long? = date?.time
}
