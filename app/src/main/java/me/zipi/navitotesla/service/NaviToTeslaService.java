package me.zipi.navitotesla.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import me.zipi.navitotesla.AppRepository;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.db.PoiAddressEntity;
import me.zipi.navitotesla.model.ShareRequest;
import me.zipi.navitotesla.model.TeslaApiResponse;
import me.zipi.navitotesla.model.TeslaRefreshTokenRequest;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.util.PreferencesUtil;
import retrofit2.Response;


public class NaviToTeslaService {

    private final Context context;
    private final Pattern pattern = Pattern.compile("^(?:[가-힣]+\\s[가-힣]+[시군구]|세종시\\s[가-힣\\d]+[읍면동로])");
    AppRepository appRepository;

    public NaviToTeslaService(Context context) {
        this.context = context;
        appRepository = AppRepository.getInstance(context, AppDatabase.getInstance(context));
    }

    private boolean isAddress(String text) {
        return pattern.matcher(text).find();
    }

    private void makeToast(String text) {
        try {
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, text, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 안내 종료
     */
    public void notificationClear() {
        PreferencesUtil.put(context, "lastAddress", "");
    }

    public void share(String packageName, String notificationTitle, String notificationText) {
        try {
            PoiFinder poiFinder = PoiFinderFactory.getPoiFinder(packageName);
            if (poiFinder == null) {
                makeToast("지원하지 않는 내비입니다.");
                return;
            }
            String poiName = poiFinder.parseDestination(notificationText);
            if (poiName.length() == 0 || poiFinder.isIgnore(notificationTitle, notificationText)) {
                PreferencesUtil.put(context, "lastAddress", "");
            }

            PoiAddressEntity poiAddressEntity = appRepository.getPoiSync(poiName);
            // 10 days cache
            String address;
            if (poiAddressEntity != null && !poiAddressEntity.isExpire()) {
                address = poiAddressEntity.getAddress();
            } else if (isAddress(poiName)) {
                address = poiName;
            } else {
                address = poiFinder.findPoiAddress(poiName);
                appRepository.savePoi(poiName, address);
            }
            String lastAddress = PreferencesUtil.getString(context, "lastAddress", "");

            if (!lastAddress.equals(address)) {
                PreferencesUtil.put(context, "lastAddress", address);
                if (address.length() > 0) {
                    makeToast("목적지 전송 요청\n" + address);

                    refreshToken();
                    Long id = loadVehicleId();
                    Response<TeslaApiResponse.ObjectType<TeslaApiResponse.Result>> response = appRepository.getTeslaApi().share(id, new ShareRequest(address)).execute();
                    TeslaApiResponse.ObjectType<TeslaApiResponse.Result> result = null;
                    if (response.isSuccessful()) {
                        result = response.body();
                    }
                    if (result != null && result.getError() == null && result.getResponse() != null && result.getResponse().getResult()) {
                        makeToast("목적지 전송 성공\n" + address);
                    } else {
                        Log.w(NaviToTeslaService.class.getName(), response.toString());
                        makeToast("목적지 전송 실패" + (result != null && result.getErrorDescription() != null ? "\n" + result.getErrorDescription() : ""));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(NaviToTeslaService.class.getName(), "thread inside error", e);
        }
    }


    public Token getToken() {
        return PreferencesUtil.loadToken(context);
    }

    public Token refreshToken() {
        return this.refreshToken(null);
    }

    public Token refreshToken(String refreshToken) {
        if (refreshToken == null) {
            Token token = PreferencesUtil.loadToken(context);
            if (token == null || !token.isExpire()) {
                return null;
            } else {
                refreshToken = token.getRefreshToken();
            }
        }

        Token token = null;
        try {
            Response<Token> newToken = appRepository.getTeslaAuthApi().refreshAccessToken(new TeslaRefreshTokenRequest(refreshToken)).execute();

            if (newToken.isSuccessful() && newToken.body() != null) {
                PreferencesUtil.saveToken(context, newToken.body());
                token = newToken.body();
            }
        } catch (Exception e) {
            Log.w(this.getClass().getName(), "refresh token fail", e);
            makeToast("Token 갱신에 실패하였습니다.");
        }
        return token;
    }


    public List<Vehicle> getVehicles() {
        Token token = PreferencesUtil.loadToken(context);

        if (token == null || token.getRefreshToken() == null) {
            makeToast("Token 설정이 필요합니다.");
            return new ArrayList<>();
        }
        List<Vehicle> vehicles = new ArrayList<>();
        try {
            refreshToken();
            Response<TeslaApiResponse.ListType<Vehicle>> response = appRepository.getTeslaApi().vehicles().execute();
            if (response.code() == 401) {
                makeToast("Token이 잘못되었습니다.");
            } else if (response.isSuccessful() && response.body() != null) {
                vehicles = response.body().getResponse();
            } else {
                Log.w(this.getClass().getName(), "get vehicle error: " + response.toString());
            }
        } catch (Exception e) {
            Log.w(this.getClass().getName(), "get vehicle error", e);
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
        return PreferencesUtil.getLong(context, "vehicleId", 0L);
    }
}
