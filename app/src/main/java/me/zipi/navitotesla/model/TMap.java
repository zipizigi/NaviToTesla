package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

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

        public String getRoadAddress(boolean withLocalName) {
            if (roadName.length() > 0 && firstBuildNo.length() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(upperAddrName);
                if (middleAddrName.length() > 0) {
                    sb.append(" ").append(middleAddrName);
                }
                if (roadName.length() > 0) {
                    sb.append(" ").append(roadName);
                }
                if (firstBuildNo.length() > 0) {
                    sb.append(" ").append(firstBuildNo);
                }
                if (secondBuildNo.length() > 0 && !secondBuildNo.equals("0")) {
                    sb.append("-").append(secondBuildNo);
                }

                // 법정동(동/로/가)가 있을 경우 추가항목으로 (법정동)을 붙여준다.
                // 건물명이 있을 경우 (법정동, 건물명) 표시도 가능하다.
                if (lowerAddrName.length() > 0 && withLocalName) {
                    String lastChar = lowerAddrName.substring(lowerAddrName.length() - 1);
                    if (lastChar.equals("동") || lastChar.equals("로") || lastChar.equals("가")) {
                        sb.append(" (").append(lowerAddrName).append(")");
                    }

                }
                return sb.toString();
            }
            return "";
        }

        public String getAddress() {
            if (firstNo == null || firstNo.length() == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(upperAddrName);
            if (middleAddrName.length() > 0) {
                sb.append(" ").append(middleAddrName);
            }
            if (lowerAddrName.length() > 0) {
                sb.append(" ").append(lowerAddrName);
            }

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
