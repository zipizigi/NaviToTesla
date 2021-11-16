package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class ShareRequest {
    String type;
    String locale;
    @SerializedName("timestamp_ms")
    Long timestampMs;
    Value value;

    public ShareRequest(String address) {
        this.timestampMs = System.currentTimeMillis() / 1000L;
        this.type = "share_ext_content_raw";
        this.locale = "ko-KR";
        this.value = new Value(address);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Value {
        @SerializedName("android.intent.extra.TEXT")
        String text;
    }

}
