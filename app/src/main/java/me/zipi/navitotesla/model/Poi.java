package me.zipi.navitotesla.model;

import java.util.Locale;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString(of = "poiName")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Poi {
    String poiName;
    @Getter(AccessLevel.NONE)
    String roadAddress;
    @Getter(AccessLevel.NONE)
    String address;
    String longitude;
    String latitude;

    public boolean isAddressEmpty() {
        return (roadAddress == null || roadAddress.equals("")) && (address == null || address.equals(""));
    }

    public String getRoadAddress() {
        // roadAddress, address, gps
        if (roadAddress != null && roadAddress.length() > 0) {
            return roadAddress;
        } else {
            return getAddress();
        }
    }

    public String getAddress() {
        if (address != null && address.length() > 0) {
            return address;
        } else {
            return getGpsAddress();
        }
    }

    public String getGpsAddress() {
        return String.format(Locale.getDefault(), "%s,%s", latitude, longitude);
    }

}
