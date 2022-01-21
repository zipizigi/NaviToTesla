package me.zipi.navitotesla.util;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

import androidx.annotation.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Response;

@RequiredArgsConstructor
public class HttpRetryInterceptor implements Interceptor {
    private final int maxRetryCount;

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {

        Response response = null;
        int retry = 0;
        boolean isSuccess = false;
        while (true) {
            try {
                if (response != null) {
                    response.close();
                    AnalysisUtil.log("retry http request #" + retry + " - " + chain.request().url().uri().getPath());
                }
                response = chain.proceed(chain.request());
                isSuccess = response.isSuccessful();
            } catch (UnknownHostException | SocketTimeoutException e) {
                AnalysisUtil.info("Network unstable...#" + retry + " " + e.getClass().getName());
                isSuccess = false;
            }

            if (isSuccess) {
                break;
            } else if (retry >= maxRetryCount) {
                response = chain.proceed(chain.request());
                break;
            } else {
                retry++;
            }
            try {
                long sleep = retry * retry * 100L;
                if (sleep > 5000) {
                    sleep = 5000;
                }
                Thread.sleep(sleep);
                AnalysisUtil.info(String.format(Locale.getDefault(), "retry sleep... %dms", sleep));
            } catch (Exception ignore) {

            }
        }

        return response;
    }
}
