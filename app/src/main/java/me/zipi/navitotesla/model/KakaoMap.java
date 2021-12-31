package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KakaoMap {

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Place {
        private final static Pattern pattern = Pattern.compile("([^\\s]+)\\s(?:산\\s)?\\d+(?:-\\d)?$");
        String id;
        @SerializedName("place_name")
        String placeName;
        @SerializedName("address_name")
        String addressName;
        @SerializedName("road_address_name")
        @Getter(AccessLevel.NONE)
        String roadAddressName;

        @SerializedName("x")
        String longitude;
        @SerializedName("y")
        String latitude;

        public String getRoadAddressName() {
            // 시도 구군구 읍동면리 (산) 123(-2)
            String address = roadAddressName;
            Matcher match = pattern.matcher(addressName);
            if (match.find()) {
                String lowerAddrName = match.group(1);
                if (lowerAddrName != null && lowerAddrName.length() > 0) {
                    String lastChar = lowerAddrName.substring(lowerAddrName.length() - 1);
                    if (lastChar.equals("동") || lastChar.equals("로") || lastChar.equals("가")) {
                        address += " (" + lowerAddrName + ")";
                    }

                }
            }
            return address;
        }
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Response<T> {
        List<T> documents;
    }
}
