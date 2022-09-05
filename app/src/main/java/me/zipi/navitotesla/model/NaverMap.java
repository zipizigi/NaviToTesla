package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class NaverMap {
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Response {
        Result result;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Result {
        Site site;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Site {
        List<Place> list;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Place {
        String name;
        String address; // 지번주소
        String roadAddress; // 도로명주소
        String abbrAddress; // 지번만. 봉래동2가 122-21

        @SerializedName("x")
        String longitude;
        @SerializedName("y")
        String latitude;


        public String getRoadAddressName(boolean withLocalName) {
            if (withLocalName && roadAddress != null && abbrAddress != null) {
                return roadAddress + " (" + abbrAddress.split(" ")[0] + ")";
            }
            return roadAddress;

        }
    }
}
