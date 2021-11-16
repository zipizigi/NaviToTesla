package me.zipi.navitotesla.service;

import android.util.Log;

import com.skt.Tmap.TMapData;
import com.skt.Tmap.poi_item.TMapPOIItem;

import java.util.List;
import java.util.Locale;

public class TMapPoiFinder implements PoiFinder {
    @Override
    public String findPoiAddress(String poiName) {
        String address = "";
        try {
            TMapData tMapData = new TMapData();
            List<TMapPOIItem> items = tMapData.findTitlePOI(poiName);
            int sameCount = 0;

            for (TMapPOIItem poi : items) {
                if (poi.getPOIName().equals(poiName)) {
                    sameCount++;
                    if (poi.newAddressList.size() > 0 && poi.newAddressList.get(0).roadName.trim().length() > 0
                            && poi.newAddressList.get(0).bldNo1.trim().length() > 0) {
                        // 도로명
                        address = poi.newAddressList.get(0).fullAddressRoad;

                    } else if (getOldAddress(poi).length() > 0) {
                        // 지번
                        address = getOldAddress(poi);
                    } else {
                        // gps
                        address = String.format(Locale.getDefault(), "%s,%s", ((Double) poi.getPOIPoint().getLatitude()).toString(),
                                ((Double) poi.getPOIPoint().getLongitude()).toString());
                    }

                }
            }
            if (sameCount > 1) {
                // 중복지명이 있으므로 전송 안함.
                return "";
            }

        } catch (Exception e) {
            Log.w(this.getClass().getName(), "TMap Api call fail", e);
        }

        return address;
    }

    @Override
    public String parseDestination(String notificationText) {

        /*
            안심주행
            출발지 > 목적지
            출발지 > 경유지 > 목적지
            출발지 > 경유지1 > 경유지2 > 목적지
         */
        return notificationText.split(">")[notificationText.split(">").length - 1].trim();

    }

    @Override
    public boolean isIgnore(String notificationTitle, String notificationText) {
        return notificationText.equals("안심주행");
    }

    private String getOldAddress(TMapPOIItem poiItem) {
        //upperAddrName : 경기, middleAddrName : 화성시, lowerAddrName : 영천동, detailAddrName : null, firstNo : 24, secondNo : 1

        StringBuilder sb = new StringBuilder();
        sb.append(poiItem.upperAddrName).append(" ").append(poiItem.middleAddrName);
        if (poiItem.lowerAddrName.length() > 0) {
            sb.append(" ").append(poiItem.lowerAddrName);
        }
        if (poiItem.detailAddrName.length() > 0) {
            sb.append(" ").append(poiItem.detailAddrName);
        }
        if (poiItem.mlClass != null && poiItem.mlClass.equals("2")) {
            sb.append(" ").append("산");
        }
        if (poiItem.firstNo.length() > 0) {
            sb.append(" ").append(poiItem.firstNo);
        } else {
            // 지번 첫부분이 없을 경우 (~~동, ~~리) gps 좌표 이용
            return "";
        }
        if (poiItem.secondNo != null && poiItem.secondNo.length() > 0 && !poiItem.secondNo.equals("0")) {
            sb.append("-").append(poiItem.secondNo);
        }


        return sb.toString();
    }
}
