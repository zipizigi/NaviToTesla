package me.zipi.navitotesla;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.lifecycle.MutableLiveData;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AppUpdaterUtil;
import me.zipi.navitotesla.util.TMapSdk;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    MutableLiveData<List<Vehicle>> vehicleListLiveData = new MutableLiveData<>();
    MutableLiveData<Token> tokenLiveData = new MutableLiveData<>();

    private NaviToTeslaService naviToTeslaService;
    private Executor executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TMapSdk.init(this);
        executor = Executors.newSingleThreadExecutor();
        vehicleListLiveData = new MutableLiveData<>();
        tokenLiveData = new MutableLiveData<>();
        setContentView(R.layout.activity_main);

        this.naviToTeslaService = new NaviToTeslaService(this);

        vehicleListLiveData.observe(this, vehicles -> updateSpinner());
        tokenLiveData.observe(this, this::updateToken);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!permissionGranted()) {
            Intent intent = new Intent(
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        }
        executor.execute(() -> tokenLiveData.postValue(naviToTeslaService.getToken()));
        AppUpdaterUtil.dialog(this);
    }

    private boolean permissionGranted() {
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(this);
        return sets.contains(getPackageName());
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

                List<Vehicle> vehicleList = naviToTeslaService.getVehicles();

                if (vehicleList.size() > 0) {
                    if (naviToTeslaService.loadVehicleId().equals(0L)) {
                        naviToTeslaService.saveVehicleId(vehicleList.get(0).getId());
                    }
                }
                vehicleListLiveData.postValue(vehicleList);
                if (tokenLiveData.getValue() == null || !tokenLiveData.getValue().equals(token)) {
                    tokenLiveData.postValue(token);
                }
                context.runOnUiThread(() -> findViewById(R.id.btnSave).setEnabled(true));
            } catch (Exception e) {
                Log.e(MainActivity.class.getName(), "thread inside error", e);
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
}