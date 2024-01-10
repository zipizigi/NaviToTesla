package me.zipi.navitotesla.model

import com.google.gson.annotations.SerializedName

data class Vehicle(
    val id: Long,
    @SerializedName("vehicle_id")
    val vehicleId: Long,
    @SerializedName("display_name")
    val displayName: String,
    val state: String,
)
