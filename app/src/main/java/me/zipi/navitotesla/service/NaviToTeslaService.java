package me.zipi.navitotesla.service;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.perf.metrics.AddTrace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import me.zipi.navitotesla.AppRepository;
import me.zipi.navitotesla.BuildConfig;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.PoiAddressEntity;
import me.zipi.navitotesla.exception.DuplicatePoiException;
import me.zipi.navitotesla.exception.ForbiddenException;
import me.zipi.navitotesla.exception.IgnorePoiException;
import me.zipi.navitotesla.exception.NotSupportedNaviException;
import me.zipi.navitotesla.model.ShareRequest;
import me.zipi.navitotesla.model.TeslaApiResponse;
import me.zipi.navitotesla.model.TeslaRefreshTokenRequest;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.PreferencesUtil;
import me.zipi.navitotesla.util.ResponseCloser;
import retrofit2.Response;


public class NaviToTeslaService {

    private final Context context;
    private final Pattern pattern = Pattern.compile("^(?:[가-힣]+\\s[가-힣]+[시군구]|세종시\\s[가-힣\\d]+[읍면동로])");
    AppRepository appRepository;

    public NaviToTeslaService(Context context) {
        this.context = context.getApplicationContext();
        appRepository = AppRepository.getInstance(this.context, AppDatabase.getInstance(this.context));
    }

    public boolean isAddress(String text) {
        return pattern.matcher(text).find();
    }

    private void makeToast(String text) {
        try {
            Log.i(this.getClass().getName(), text);
            AnalysisUtil.log(text);
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, text, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            AnalysisUtil.recordException(e);
            e.printStackTrace();
        }

    }

    /**
     * 안내 종료
     */
    public void notificationClear() {
        PreferencesUtil.put(context, "lastAddress", "");
    }

    @AddTrace(name = "share")
    public void share(String packageName, String notificationTitle, String notificationText) {
        AnalysisUtil.setCustomKey("packageName", packageName);
        AnalysisUtil.setCustomKey("notificationTitle", notificationTitle);
        AnalysisUtil.setCustomKey("notificationText", notificationText);

        Bundle eventParam = new Bundle();
        eventParam.putString("package", packageName);

        try {

            String address = getAddress(packageName, notificationTitle, notificationText);
            String lastAddress = PreferencesUtil.getString(context, "lastAddress", "");

            if (!lastAddress.equals(address)) {
                try {
                    share(address);
                } catch (ForbiddenException e) {
                    AnalysisUtil.log("force expire token and retry...");
                    expireToken();
                    share(address);
                }
            } else {
                // 마지막 전송 주소와 동일
                // makeToast("목적지 전송 무시\n이전에 전송 요청한 주소와 동일함.");
                AnalysisUtil.logEvent("previous_request_address", eventParam);
            }
            appRepository.clearExpiredPoi();
        } catch (DuplicatePoiException e) {
            AnalysisUtil.logEvent("duplicated_address", eventParam);
            makeToast("목적지 전송 실패\n목적지 중복");
        } catch (NotSupportedNaviException e) {
            AnalysisUtil.logEvent("unsupported_navi", eventParam);
            AnalysisUtil.recordException(e);
            makeToast("목적지 전송 실패\n미지원 내비");
        } catch (IgnorePoiException e) {
            AnalysisUtil.logEvent("ignore_address", eventParam);
        } catch (ForbiddenException e) {
            makeToast("목적지 전송 실패\n인증 실패");
            AnalysisUtil.logEvent("error_share", eventParam);
            AnalysisUtil.recordException(e);
        } catch (Exception e) {
            Log.e(NaviToTeslaService.class.getName(), "thread inside error", e);
            makeToast("목적지 전송 실패\n내부 또는 API 오류");
            AnalysisUtil.logEvent("error_share", eventParam);
            AnalysisUtil.recordException(e);
            AnalysisUtil.sendUnsentReports();
        }
    }


    public void share(String address) throws IOException, ForbiddenException {
        PreferencesUtil.put(context, "lastAddress", address);
        if (address.length() > 0) {
            makeToast("목적지 전송 요청\n" + address);

            if (refreshToken() == null) {
                return;
            }
            Long id = loadVehicleId();
            if (BuildConfig.DEBUG) {
                makeToast("[DEBUG] 목적지 전송 Skip\n" + address);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignore) {

                }
                return;
            }
            Response<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>> response = appRepository.getTeslaApi().share(id, new ShareRequest(address)).execute();
            TeslaApiResponse.ObjectType<TeslaApiResponse.Result> result = null;
            if (response.isSuccessful()) {
                result = response.body();
            }
            if (result != null && result.getError() == null && result.getResponse() != null && result.getResponse().getResult()) {
                makeToast("목적지 전송 성공\n" + address);
                AnalysisUtil.log("send_success");
            } else {
                Log.w(NaviToTeslaService.class.getName(), response.toString());
                makeToast("목적지 전송 실패" + (result != null && result.getErrorDescription() != null ? "\n" + result.getErrorDescription() : ""));

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
                AnalysisUtil.recordException(exception);
                throw exception;
            }
            ResponseCloser.closeAll(response);
        }
    }

    @AddTrace(name = "getAddress")
    private String getAddress(String packageName, String notificationTitle, String notificationText)
            throws NotSupportedNaviException, DuplicatePoiException, IgnorePoiException, IOException {
        Bundle eventParam = new Bundle();
        eventParam.putString("package", packageName);

        PoiFinder poiFinder = PoiFinderFactory.getPoiFinder(packageName);

        String poiName = poiFinder.parseDestination(notificationText);
        if (poiName.length() == 0 || poiFinder.isIgnore(notificationTitle, notificationText)) {
            AnalysisUtil.logEvent("address_ignore_or_not_found", eventParam);
            throw new IgnorePoiException(packageName);
        }


        PoiAddressEntity poiAddressEntity = appRepository.getPoiSync(poiName);
        // 10 days cache
        String address;
        if (poiAddressEntity != null && !poiAddressEntity.isExpire()) {
            address = poiAddressEntity.getAddress();
            AnalysisUtil.logEvent("address_parse_cache", eventParam);
        } else if (isAddress(poiName)) {
            address = poiName;
            AnalysisUtil.logEvent("address_direct", eventParam);
        } else {
            address = poiFinder.findPoiAddress(poiName);
            AnalysisUtil.logEvent("address_parse_api", eventParam);
            appRepository.savePoi(poiName, address, false);
        }
        return address;
    }

    public Token getToken() {
        return PreferencesUtil.loadToken(context);
    }

    public void expireToken() {
        PreferencesUtil.expireToken(context);
    }

    @AddTrace(name = "refreshToken")
    public Token refreshToken() {
        return this.refreshToken(null);
    }

    public Token refreshToken(String refreshToken) {
        if (refreshToken == null) {
            Token token = PreferencesUtil.loadToken(context);
            if (token == null) {
                makeToast("Token 정보가 없습니다.");
                return null;
            } else if (!token.isExpire()) {
                return token;
            } else {
                refreshToken = token.getRefreshToken();
            }
        }

        AnalysisUtil.log("Start refresh access token");
        Token token = null;
        try {
            Response<Token> newToken = appRepository.getTeslaAuthApi().refreshAccessToken(new TeslaRefreshTokenRequest(refreshToken)).execute();

            if (newToken.isSuccessful() && newToken.body() != null) {
                PreferencesUtil.saveToken(context, newToken.body());
                token = newToken.body();
                AnalysisUtil.log("Success refresh access token");
            }
            ResponseCloser.closeAll(newToken);
        } catch (Exception e) {
            Log.w(this.getClass().getName(), "refresh token fail", e);
            makeToast("Token 갱신에 실패하였습니다.");
            AnalysisUtil.recordException(e);
        }
        if (token == null) {
            AnalysisUtil.log("fail refresh access token. token is null");
        }
        return token;
    }


    @AddTrace(name = "getVehicles")
    public List<Vehicle> getVehicles(Token token) {
        if (token == null) {
            token = PreferencesUtil.loadToken(context);
        }
        if (token == null || token.getRefreshToken() == null) {
            makeToast("Token 설정이 필요합니다.");
            return new ArrayList<>();
        }
        List<Vehicle> vehicles = new ArrayList<>();
        try {
            if (refreshToken() == null) {
                return vehicles;
            }
            Response<TeslaApiResponse.ListType<Vehicle>> response = appRepository.getTeslaApi().vehicles().execute();
            if (response.code() == 401) {
                makeToast("Token이 잘못되었습니다.");
            } else if (response.isSuccessful() && response.body() != null) {
                vehicles = response.body().getResponse();
            } else {
                Log.w(this.getClass().getName(), "get vehicle error: " + response.toString());
            }
            ResponseCloser.closeAll(response);

        } catch (Exception e) {
            Log.w(this.getClass().getName(), "get vehicle error", e);
            AnalysisUtil.recordException(e);
        }
        if (vehicles == null) {
            vehicles = new ArrayList<>();
        }
        if (vehicles.size() == 0) {
            makeToast("등록된 차량이 없습니다.");
        }
        return vehicles;
    }

    public void saveVehicleId(Long id) {
        PreferencesUtil.put(context, "vehicleId", id);
    }

    public Long loadVehicleId() {
        Long vid = PreferencesUtil.getLong(context, "vehicleId", 0L);
        return vid == null ? 0L : vid;
    }

    public void clearPoiCache() {
        appRepository.clearAllPoi();
    }
}
