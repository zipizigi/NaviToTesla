package me.zipi.navitotesla.util;

import android.util.Log;

import java.util.Objects;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import retrofit2.Response;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResponseCloser {
    public static void closeAll(Response<?> response) {
        close(response);
        closeError(response);
    }

    public static void close(Response<?> response) {
        try {
            Objects.requireNonNull(response.raw().body()).close();
        } catch (IllegalStateException ignore) {
        } catch (Exception e) {
            Log.w(ResponseCloser.class.getName(), "Http response close error", e);
            AnalysisUtil.log("Http response close error");
            AnalysisUtil.recordException(e);
        }
    }

    public static void closeError(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                response.errorBody().close();
            }
        } catch (Exception e) {
            Log.w(ResponseCloser.class.getName(), "Http response close error", e);
            AnalysisUtil.log("Http response close error");
            AnalysisUtil.recordException(e);
        }
    }
}
