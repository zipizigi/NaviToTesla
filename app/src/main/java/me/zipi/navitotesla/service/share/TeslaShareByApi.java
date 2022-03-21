package me.zipi.navitotesla.service.share;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;

import androidx.annotation.NonNull;
import me.zipi.navitotesla.AppRepository;
import me.zipi.navitotesla.BuildConfig;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.exception.ForbiddenException;
import me.zipi.navitotesla.model.ShareRequest;
import me.zipi.navitotesla.model.TeslaApiResponse;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.ResponseCloser;
import retrofit2.Response;

public class TeslaShareByApi extends TeslaShareBase implements TeslaShare {
    private final Long vehicleId;

    public TeslaShareByApi(@NonNull Context context, Long vehicleId) {
        super(context);
        this.vehicleId = vehicleId;
    }

    @Override
    public void share(String address) throws IOException {
        if (BuildConfig.DEBUG) {
            AnalysisUtil.makeToast(context, "[DEBUG] 목적지 전송 Skip\n" + address);
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {

            }
            return;
        }
        AnalysisUtil.log("share using tesla api share");
        AppRepository appRepository = AppRepository.getInstance(this.context, AppDatabase.getInstance(this.context));
        Response<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>> response = appRepository.getTeslaApi().share(vehicleId, new ShareRequest(address)).execute();
        TeslaApiResponse.ObjectType<TeslaApiResponse.Result> result = null;
        if (response.isSuccessful()) {
            result = response.body();
        }
        if (result != null && result.getError() == null && result.getResponse() != null && result.getResponse().getResult()) {
            AnalysisUtil.makeToast(context, context.getString(R.string.sendDestinationSuccess) + "\n" + address);

            AnalysisUtil.log("send_success");
            AnalysisUtil.logEvent("share_by_api_success", new Bundle());
        } else {
            Log.w(NaviToTeslaService.class.getName(), response.toString());
            AnalysisUtil.makeToast(context, context.getString(R.string.sendDestinationFail) + (result != null && result.getErrorDescription() != null ? "\n" + result.getErrorDescription() : ""));

            AnalysisUtil.log("send_fail");
            AnalysisUtil.setCustomKey("address", address);
            if (result != null && result.getErrorDescription() != null) {
                AnalysisUtil.log("errorDescription: " + result.getErrorDescription());
            }
            if (!response.isSuccessful()) {
                AnalysisUtil.log("Http response code: " + response.code());
                if (response.errorBody() != null) {
                    AnalysisUtil.log("Http error response: " + response.errorBody().string());
                }
            }

            RuntimeException exception;
            if (response.code() == 401) {
                String errorString = "";
                if (response.errorBody() != null) {
                    errorString = response.errorBody().string();
                }
                exception = new ForbiddenException(401, errorString);
            } else {
                exception = new RuntimeException("Send address fail");
            }
            AnalysisUtil.logEvent("share_by_api_fail", new Bundle());
            AnalysisUtil.recordException(exception);
            throw exception;
        }
        ResponseCloser.closeAll(response);
    }
}
