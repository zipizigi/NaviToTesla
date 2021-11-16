package me.zipi.navitotesla.model;

import com.google.gson.annotations.SerializedName;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TeslaApiResponse {

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ListType<T> {
        java.util.List<T> response;
        Integer count;
        String error;
        @SerializedName("error_description")
        String errorDescription;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class ObjectType<T> {
        T response;
        Integer count;
        String error;
        @SerializedName("error_description")
        String errorDescription;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class Result {
        Boolean result;
        Boolean queued;
    }
}
