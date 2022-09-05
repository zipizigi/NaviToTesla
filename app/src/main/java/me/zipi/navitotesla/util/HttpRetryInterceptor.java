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

    private void sleep(int retry, Chain chain) {
        try {
            long sleep = retry * retry * 100L / 2;
            sleep = sleep > 3000 ? 3000 : sleep;

            if (sleep > 0) {
                Thread.sleep(sleep);
                AnalysisUtil.log("retry http request #" + retry + " - " + chain.request().url().uri().getPath());
                AnalysisUtil.info(String.format(Locale.getDefault(), "retry sleep... %dms", sleep));
            }
        } catch (Exception ignore) {

        }
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {

        Response response = null;
        int retry = 0;
        boolean isSuccess;
        while (true) {
            sleep(retry, chain);

            try {
                response = chain.proceed(chain.request());
                isSuccess = response.isSuccessful();
                if (!isSuccess) {
                    AnalysisUtil.log("http status code : " + response.code());
                }
            } catch (UnknownHostException | SocketTimeoutException e) {
                AnalysisUtil.info("Network unstable...#" + retry + " " + e.getClass().getName());
                isSuccess = false;
            }

            if (isSuccess) {
                break;
            } else if (response != null && response.code() >= 400 && response.code() <= 405) {
                AnalysisUtil.info("Http call 4xx error!: " + response.code());
                break;
            } else if (retry >= maxRetryCount) {
                if (response == null) {
                    response = chain.proceed(chain.request());
                }
                break;
            } else {
                if (response != null) {
                    response.close();
                    response = null;
                }
                retry++;
            }

        }

        return response;
    }
}
