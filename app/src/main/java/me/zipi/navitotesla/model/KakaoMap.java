package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KakaoMap {

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Place {
        String id;
        @SerializedName("place_name")
        String placeName;
        @SerializedName("address_name")
        String addressName;
        @SerializedName("road_address_name")
        String roadAddressName;

        @SerializedName("x")
        String longitude;
        @SerializedName("y")
        String latitude;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Response<T> {
        List<T> documents;
    }
}
