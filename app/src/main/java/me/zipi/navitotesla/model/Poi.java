package me.zipi.navitotesla.model;

import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(of = "poiName")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Poi {
    String poiName;
    String roadAddress;
    String address;
    String longitude;
    String latitude;

    public boolean isAddressEmpty() {
        return (roadAddress == null || roadAddress.equals("")) && (address == null || address.equals(""));
    }

    public String getFinalAddress() {
        // roadAddress, address, gps
        if (roadAddress != null && roadAddress.length() > 0) {
            return roadAddress;
        } else if (address != null && address.length() > 0) {
            return address;
        } else {
            return String.format(Locale.getDefault(), "%s,%s", latitude, longitude);
        }
    }

    public String getGpsAddress() {
        return String.format(Locale.getDefault(), "%s,%s", latitude, longitude);
    }

}
