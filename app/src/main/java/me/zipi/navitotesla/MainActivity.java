package me.zipi.navitotesla;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.MutableLiveData;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.AppUpdaterUtil;
import me.zipi.navitotesla.util.PreferencesMigrationUtil;
import me.zipi.navitotesla.util.PreferencesUtil;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    MutableLiveData<List<Vehicle>> vehicleListLiveData = new MutableLiveData<>();
    MutableLiveData<Token> tokenLiveData = new MutableLiveData<>();

    private NaviToTeslaService naviToTeslaService;
    private Executor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferencesMigrationUtil.migration(this);
        executor = Executors.newFixedThreadPool(2);
        vehicleListLiveData = new MutableLiveData<>();
        tokenLiveData = new MutableLiveData<>();
        setContentView(R.layout.activity_main);

        this.naviToTeslaService = new NaviToTeslaService(this);

        vehicleListLiveData.observe(this, vehicles -> updateSpinner());
        tokenLiveData.observe(this, this::updateToken);

        ((TextView) findViewById(R.id.txtVersion)).setText(getPackageVersion());
        ((TextView) findViewById(R.id.txtAccessToken)).setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onResume() {
        super.onResume();
        permissionGrantedCheck();

        executor.execute(() -> tokenLiveData.postValue(naviToTeslaService.getToken()));
        AppUpdaterUtil.dialog(this);

    }

    private void permissionGrantedCheck() {
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        if (!sets.contains(getPackageName())) {
            new AlertDialog.Builder(this)
                    .setTitle("권한 요청")
                    .setMessage("Navi To Tesla를 이용하려면 알림 접근 권한이 필요합니다.\nNavi To Telsa에 권한을 허용해주세요.")
                    // .setIcon(R.drawable.ic_launcher_background)
                    .setPositiveButton("확인", (dialog, which) ->
                            startActivity(new Intent(
                                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    )
                    .setCancelable(false)
                    .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.executor = null;
        this.naviToTeslaService = null;
    }

    public void updateToken(Token token) {
        runOnUiThread(() -> {
            if (token != null) {
                ((EditText) findViewById(R.id.txtRefreshToken)).setText(token.getRefreshToken());
                ((TextView) findViewById(R.id.txtAccessToken)).setText(token.getAccessToken());
            }
        });
        if (token != null) {
            onBtnSaveClick(token.getRefreshToken());
        }
    }

    public void onTxtVersionClicked(View view) {
        new AlertDialog.Builder(this)
                .setTitle("최신버전 다운로드")
                .setMessage("최신버전 다운로드 페이지로 이동하시겠습니까?")
                // .setIcon(R.drawable.ic_launcher_background)
                .setPositiveButton("예", (dialog, which) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/zipizigi/NaviToTesla/releases/latest"))))
                .setNegativeButton("아니오", (dialog, which) -> {

                })
                .show();
    }

    public void onBtnPoiCacheClearClick(View view) {
        this.runOnUiThread(() -> findViewById(R.id.btnPoiCacheClear).setEnabled(false));
        executor.execute(() -> {
                    try {
                        naviToTeslaService.clearPoiCache();
                        AppUpdaterUtil.clearDoNoyShow(this);
                    } catch (Exception e) {
                        Log.w(MainActivity.class.getName(), "clear poi cache error", e);
                        AnalysisUtil.recordException(e);
                    }
                    this.runOnUiThread(() -> findViewById(R.id.btnPoiCacheClear).setEnabled(true));
                }
        );
    }

    public void onBtnPasteClick(View view) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.getPrimaryClip() != null
                && clipboard.getPrimaryClip().getItemAt(0) != null) {
            String pasteData = clipboard.getPrimaryClip().getItemAt(0).getText().toString().trim();
            if (pasteData.matches("(^[\\w-]*\\.[\\w-]*\\.[\\w-]*$)")) {
                ((EditText) findViewById(R.id.txtRefreshToken)).setText(pasteData);
            }
        }
    }

    public void onBtnTokenClearClick(View view) {
        ((EditText) findViewById(R.id.txtRefreshToken)).setText("");
        ((TextView) findViewById(R.id.txtAccessToken)).setText("");
        vehicleListLiveData.postValue(new ArrayList<>());

        executor.execute(() -> {
            PreferencesUtil.clear(this);
            tokenLiveData.postValue(naviToTeslaService.getToken());
        });

    }

    public void onBtnSaveClick(View view) {
        String refreshToken = ((EditText) findViewById(R.id.txtRefreshToken)).getText().toString().trim();

        onBtnSaveClick(refreshToken);
    }

    public void onBtnSaveClick(String refreshToken) {
        if (refreshToken == null || refreshToken.length() == 0) {
            return;
        }

        this.runOnUiThread(() -> {
                    findViewById(R.id.btnSave).setEnabled(false);
                    ((Button) findViewById(R.id.btnSave)).setText("확인중");
                    View focusView = this.getCurrentFocus();
                    if (focusView != null) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(focusView.getWindowToken(), 0);
                    }
                }
        );


        final Activity context = this;
        executor.execute(() -> {
            try {
                Token token = naviToTeslaService.refreshToken(refreshToken);
                if (tokenLiveData.getValue() == null || !tokenLiveData.getValue().equals(token)) {
                    tokenLiveData.postValue(token);
                }

                List<Vehicle> vehicleList = naviToTeslaService.getVehicles(token);

                if (vehicleList.size() > 0) {
                    if (naviToTeslaService.loadVehicleId().equals(0L)) {
                        naviToTeslaService.saveVehicleId(vehicleList.get(0).getId());
                    }
                }
                vehicleListLiveData.postValue(vehicleList);

                context.runOnUiThread(() -> {
                    findViewById(R.id.btnSave).setEnabled(true);
                    ((Button) findViewById(R.id.btnSave)).setText("저장");
                });
            } catch (Exception e) {
                Log.e(MainActivity.class.getName(), "thread inside error", e);
                AnalysisUtil.recordException(e);
            }
        });
    }

    private void updateSpinner() {
        Long id = naviToTeslaService.loadVehicleId();
        List<String> spinnerArray = new ArrayList<>();
        if (vehicleListLiveData.getValue() == null) {
            return;
        }
        int spinnerIndex = 0;
        for (int i = 0; i < vehicleListLiveData.getValue().size(); i++) {
            Vehicle v = vehicleListLiveData.getValue().get(i);
            spinnerArray.add(v.getDisplayName());
            if (v.getId().equals(id)) {
                spinnerIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = findViewById(R.id.vehicleSelector);
        spinner.setAdapter(adapter);

        spinner.setSelection(spinnerIndex);
        spinner.setOnItemSelectedListener(this);

    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (vehicleListLiveData.getValue() != null) {
            Long vid = vehicleListLiveData.getValue().get(i).getId();
            naviToTeslaService.saveVehicleId(vid);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private String getPackageVersion() {
        String version = "1.0";
        try {
            version = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
        } catch (Exception e) {
            AnalysisUtil.recordException(e);
        }
        return version;
    }
}