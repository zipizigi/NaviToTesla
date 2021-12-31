package me.zipi.navitotesla.ui.home;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import me.zipi.navitotesla.background.TokenWorker;
import me.zipi.navitotesla.databinding.FragmentHomeBinding;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.AppUpdaterUtil;
import me.zipi.navitotesla.util.PreferencesMigrationUtil;
import me.zipi.navitotesla.util.PreferencesUtil;

public class HomeFragment extends Fragment implements AdapterView.OnItemSelectedListener, View.OnClickListener {

    private HomeViewModel homeViewModel;
    @Nullable
    private FragmentHomeBinding binding;
    @Nullable
    private NaviToTeslaService naviToTeslaService;
    @Nullable
    private Executor executor;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        if (this.getActivity() != null) {
            this.getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        executor = Executors.newFixedThreadPool(2);

        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        homeViewModel.getVehicleListLiveData().observe(getViewLifecycleOwner(), vehicles -> updateSpinner());
        homeViewModel.getTokenLiveData().observe(getViewLifecycleOwner(), (v) -> renderToken());
        homeViewModel.getAppVersion().observe(getViewLifecycleOwner(), (v) -> renderVersion());
        homeViewModel.getIsUpdateAvailable().observe(getViewLifecycleOwner(), (v) -> renderVersion());
        homeViewModel.getRefreshToken().observe(getViewLifecycleOwner(), this::getAccessTokenAndVehicles);

        binding.txtAccessToken.setMovementMethod(new ScrollingMovementMethod());
        KeyboardVisibilityEvent.setEventListener(
                getActivity(),
                isOpen -> {
                    if (binding != null) {
                        binding.txtVersion.setVisibility(isOpen ? View.INVISIBLE : View.VISIBLE);
                    }
                });
        binding.btnSave.setOnClickListener(this);
        binding.btnPoiCacheClear.setOnClickListener(this);
        binding.btnPaste.setOnClickListener(this);
        binding.btnTokenClear.setOnClickListener(this);
        binding.txtVersion.setOnClickListener(this);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        permissionGrantedCheck();
        if (executor != null) {
            executor.execute(this::updateToken);
            executor.execute(() -> AppUpdaterUtil.dialog(getActivity()));
            executor.execute(this::updateVersion);
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        homeViewModel.clearObserve(getViewLifecycleOwner());
        binding = null;
        executor = null;
        naviToTeslaService = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PreferencesMigrationUtil.migration(context);
        this.naviToTeslaService = new NaviToTeslaService(context);
    }

    @Override
    public void onClick(View view) {
        Integer id = view.getId();
        if (binding == null) {
            return;
        }
        if (id.equals(binding.txtVersion.getId())) {
            onTxtVersionClicked();
        } else if (id.equals(binding.btnPoiCacheClear.getId())) {
            onBtnPoiCacheClearClick();
        } else if (id.equals(binding.btnPaste.getId())) {
            onBtnPasteClick();
        } else if (id.equals(binding.btnTokenClear.getId())) {
            onBtnTokenClearClick();
        } else if (id.equals(binding.btnSave.getId())) {
            onBtnSaveClick(binding.txtRefreshToken.getText().toString().trim());
        }
    }

    private void permissionGrantedCheck() {
        if (getContext() != null) {
            Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(getContext());
            if (!sets.contains(getContext().getPackageName())) {
                new AlertDialog.Builder(getContext())
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

    }


    private void updateToken() {
        if (naviToTeslaService == null) {
            return;
        }
        homeViewModel.getTokenLiveData().postValue(naviToTeslaService.getToken());
    }

    private void renderToken() {
        if (binding == null) {
            return;
        }
        Token token = homeViewModel.getTokenLiveData().getValue();
        if (token == null) {
            binding.txtRefreshToken.setText("");
            binding.txtAccessToken.setText("");
        } else {
            binding.txtRefreshToken.setText(token.getRefreshToken());
            binding.txtAccessToken.setText(token.getAccessToken());
            homeViewModel.getRefreshToken().postValue(token.getRefreshToken());
        }
    }

    private void onTxtVersionClicked() {
        if (executor != null) {
            executor.execute(() -> AppUpdaterUtil.dialog(getActivity(), true));
        }
    }

    private void onBtnPoiCacheClearClick() {
        if (binding != null && executor != null && naviToTeslaService != null) {
            binding.btnPoiCacheClear.setEnabled(false);
            executor.execute(() -> {
                        try {
                            naviToTeslaService.clearPoiCache();
                            AppUpdaterUtil.clearDoNotShow(getContext());
                        } catch (Exception e) {
                            Log.w(this.getClass().getName(), "clear poi cache error", e);
                            AnalysisUtil.recordException(e);
                        }
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> binding.btnPoiCacheClear.setEnabled(true));
                        }
                    }
            );
        }
    }

    private void onBtnPasteClick() {
        if (getActivity() != null && binding != null) {
            ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemAt(0) != null) {
                String pasteData = clipboard.getPrimaryClip().getItemAt(0).getText().toString().trim();
                if (pasteData.matches("(^[\\w-]*\\.[\\w-]*\\.[\\w-]*$)")) {
                    binding.txtRefreshToken.setText(pasteData);
                }
            }
        }
    }

    private void onBtnTokenClearClick() {
        homeViewModel.getVehicleListLiveData().postValue(new ArrayList<>());
        homeViewModel.refreshToken.postValue("");

        if (executor != null) {
            executor.execute(() -> {
                if (getContext() != null && naviToTeslaService != null) {
                    PreferencesUtil.clear(getContext());
                    homeViewModel.getTokenLiveData().postValue(naviToTeslaService.getToken());
                    TokenWorker.cancelBackgroundWork(getContext());
                }
            });
        }
    }

    private void onBtnSaveClick(String refreshToken) {
        if (refreshToken == null || refreshToken.length() == 0) {
            return;
        }
        if (homeViewModel.getRefreshToken().getValue() != null && homeViewModel.getRefreshToken().getValue().equals(refreshToken)) {
            return;
        }
        homeViewModel.getRefreshToken().postValue(refreshToken);
    }

    private void getAccessTokenAndVehicles(String refreshToken) {
        if (refreshToken == null || refreshToken.length() == 0 || binding == null || executor == null) {
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText("확인중");
        if (getActivity() != null) {
            View focusView = getActivity().getCurrentFocus();
            if (focusView != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(focusView.getWindowToken(), 0);
            }
        }

        final Activity context = getActivity();
        executor.execute(() -> {

            try {
                if (naviToTeslaService == null) {
                    return;
                }
                Token token = naviToTeslaService.refreshToken(refreshToken);
                if (homeViewModel.getTokenLiveData().getValue() == null || !homeViewModel.getTokenLiveData().getValue().equals(token)) {
                    homeViewModel.getTokenLiveData().postValue(token);
                    TokenWorker.startBackgroundWork(context);
                }
                if (naviToTeslaService == null) {
                    return;
                }
                List<Vehicle> vehicleList = naviToTeslaService.getVehicles(token);

                if (naviToTeslaService != null && vehicleList.size() > 0) {
                    if (naviToTeslaService.loadVehicleId().equals(0L)) {
                        naviToTeslaService.saveVehicleId(vehicleList.get(0).getId());
                    }
                }
                homeViewModel.getVehicleListLiveData().postValue(vehicleList);

                if (context != null) {
                    context.runOnUiThread(() -> {
                        if (binding != null) {
                            binding.btnSave.setEnabled(true);
                            binding.btnSave.setText("저장");
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(this.getClass().getName(), "thread inside error", e);
                AnalysisUtil.recordException(e);
            }
        });
    }

    private void updateSpinner() {
        if (naviToTeslaService == null || binding == null) {
            return;
        }
        Long id = naviToTeslaService.loadVehicleId();
        List<String> spinnerArray = new ArrayList<>();
        if (homeViewModel.getVehicleListLiveData().getValue() == null) {
            return;
        }
        int spinnerIndex = 0;
        for (int i = 0; i < homeViewModel.getVehicleListLiveData().getValue().size(); i++) {
            Vehicle v = homeViewModel.getVehicleListLiveData().getValue().get(i);
            spinnerArray.add(v.getDisplayName());
            if (v.getId().equals(id)) {
                spinnerIndex = i;
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, spinnerArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = binding.vehicleSelector;
        spinner.setAdapter(adapter);

        spinner.setSelection(spinnerIndex);
        spinner.setOnItemSelectedListener(this);

    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

        if (naviToTeslaService != null && homeViewModel.getVehicleListLiveData().getValue() != null) {
            Long vid = homeViewModel.getVehicleListLiveData().getValue().get(i).getId();
            naviToTeslaService.saveVehicleId(vid);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    private void updateVersion() {
        if (executor != null) {
            executor.execute(() -> {
                homeViewModel.appVersion.postValue(AppUpdaterUtil.getCurrentVersion(this.getContext()));
                homeViewModel.isUpdateAvailable.postValue(AppUpdaterUtil.isUpdateAvailable(this.getContext()));
            });
        }
    }

    private void renderVersion() {
        if (binding == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(homeViewModel.getAppVersion().getValue());
        if (homeViewModel.getIsUpdateAvailable().getValue() != null && homeViewModel.getIsUpdateAvailable().getValue()) {
            sb.append("\n").append("(업데이트가능)");
            binding.txtVersion.setTextColor(Color.RED);
        }
        binding.txtVersion.setText(sb.toString());
    }


}