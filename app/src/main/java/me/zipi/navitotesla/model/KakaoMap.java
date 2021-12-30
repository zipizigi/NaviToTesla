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
                String middleAddrName = match.group(1);
                if (middleAddrName != null && middleAddrName.length() > 0) {
                    String lastChar = middleAddrName.substring(middleAddrName.length() - 1);
                    if (lastChar.equals("동") || lastChar.equals("로") || lastChar.equals("가")) {
                        address += " (" + middleAddrName + ")";
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
