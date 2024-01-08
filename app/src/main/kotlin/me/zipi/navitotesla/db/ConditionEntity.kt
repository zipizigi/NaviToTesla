package me.zipi.navitotesla.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "condition", indices = [Index(value = ["type", "name"], unique = true)])
class ConditionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val name: String,

    // wifi bluetooth
    val type: String,
    val created: Date,
)
