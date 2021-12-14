package me.zipi.navitotesla.model;


import com.google.gson.annotations.SerializedName;

import java.util.Calendar;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@EqualsAndHashCode(of = "refreshToken")
public class Token {
    @SerializedName("refresh_token")
    String refreshToken;
    @SerializedName("access_token")
    String accessToken;
    @SerializedName("expires_in")
    Integer expires;

    Long updated;

    public boolean isExpire() {
        return this.updated == null || this.accessToken == null
                || Math.abs(this.updated - Calendar.getInstance().getTime().getTime()) / 1000L / 60L / 60L > 7; // expire in 8 hours
    }

}
