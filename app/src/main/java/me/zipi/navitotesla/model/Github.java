package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Github {
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Release {
        Long id;
        @SerializedName("tag_name")
        String tagName;
        String name;
        List<Asset> assets;
        String body;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Asset {
        Long id;
        String name;
        @SerializedName("content_type")
        String contentType;
        @SerializedName("browser_download_url")
        String downloadUrl;
    }
}
