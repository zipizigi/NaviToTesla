package me.zipi.navitotesla.model;


import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Vehicle {

    Long id;
    @SerializedName("vehicle_id")
    Long vehicleId;

    @SerializedName("display_name")
    String displayName;

    String state;

}
