package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Locale;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TMap {
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class SearchPoiResponse {
        SearchPoiInfo searchPoiInfo;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class SearchPoiInfo {
        Integer count;
        Integer page;
        Integer totalCount;

        PoiItems pois;

    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PoiItems {
        List<PoiItem> poi;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class PoiItem {
        String name;

        String upperAddrName;
        String middleAddrName;
        String lowerAddrName;
        String detailAddrName;
        String mlClass;
        String firstNo;
        String secondNo;

        String roadName;
        String firstBuildNo;
        String secondBuildNo;

        @SerializedName("noorLat")
        String latitude;
        @SerializedName("noorLon")
        String longitude;

        public String getRoadAddress() {
            if (roadName.length() > 0 && firstBuildNo.length() > 0) {
                String roadAddress = String.format(Locale.getDefault(), "%s %s %s %s",
                        upperAddrName, middleAddrName, roadName, firstBuildNo);
                if (secondBuildNo.length() > 0 && !secondBuildNo.equals("0")) {
                    roadAddress = roadAddress + "-" + secondBuildNo;
                }
                return roadAddress;
            }
            return "";
        }

        public String getAddress() {
            if (firstNo == null || firstNo.length() == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(upperAddrName).append(" ")
                    .append(middleAddrName).append(" ")
                    .append(lowerAddrName);

            if (detailAddrName.length() > 0) {
                sb.append(" ").append(detailAddrName);
            }

            if (mlClass.equals("2")) {
                sb.append(" 산");
            }
            sb.append(" ").append(firstNo);
            if (secondNo.length() > 0 && !secondNo.equals("0")) {
                sb.append("-").append(secondNo);
            }
            return sb.toString();
        }
    }

}
