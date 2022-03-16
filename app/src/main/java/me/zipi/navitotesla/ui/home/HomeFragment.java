package me.zipi.navitotesla.ui.home;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;

import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import me.zipi.navitotesla.AppExecutors;
import me.zipi.navitotesla.BuildConfig;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.background.TokenWorker;
import me.zipi.navitotesla.databinding.FragmentHomeBinding;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.AppUpdaterUtil;
import me.zipi.navitotesla.util.PreferencesMigrationUtil;
import me.zipi.navitotesla.util.PreferencesUtil;

public class HomeFragment extends Fragment
        implements AdapterView.OnItemSelectedListener, View.OnClickListener, View.OnLongClickListener, RadioGroup.OnCheckedChangeListener {

    private HomeViewModel homeViewModel;
    @Nullable
    private FragmentHomeBinding binding;
    @Nullable
    private NaviToTeslaService naviToTeslaService;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        if (this.getActivity() != null) {
            this.getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        homeViewModel.getVehicleListLiveData().observe(getViewLifecycleOwner(), vehicles -> updateSpinner());
        homeViewModel.getTokenLiveData().observe(getViewLifecycleOwner(), (v) -> renderToken());
        homeViewModel.getAppVersion().observe(getViewLifecycleOwner(), (v) -> renderVersion());
        homeViewModel.getIsUpdateAvailable().observe(getViewLifecycleOwner(), (v) -> renderVersion());
        homeViewModel.getRefreshToken().observe(getViewLifecycleOwner(), this::getAccessTokenAndVehicles);
        homeViewModel.getIsInstalledTeslaApp().observe(getViewLifecycleOwner(), this::onChangeTeslaAppInstalled);
        homeViewModel.getShareMode().observe(getViewLifecycleOwner(), this::onChangedTeslaShareMode);

        binding.txtAccessToken.setMovementMethod(new ScrollingMovementMethod());
        binding.radioGroupShareMode.setOnCheckedChangeListener(this);
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
        binding.txtVersion.setOnLongClickListener(this);


        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        permissionGrantedCheck();

        AppExecutors.execute(this::updateVersion);
        AppExecutors.execute(this::updateToken);
        AppExecutors.execute(this::updateLatestVersion);
        AppExecutors.execute(this::updateShareMode);
        AppExecutors.execute(() -> homeViewModel.getIsInstalledTeslaApp().postValue(isTeslaAppInstalled()));
        if (permissionAlertDialog == null || !permissionAlertDialog.isShowing()) {
            AppExecutors.execute(() -> AppUpdaterUtil.dialog(getActivity()));
        }

    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        homeViewModel.clearObserve(getViewLifecycleOwner());
        binding = null;
        naviToTeslaService = null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PreferencesMigrationUtil.migration(context);
        this.naviToTeslaService = new NaviToTeslaService(context);
    }

    @Override
    public boolean onLongClick(View view) {
        if (binding == null || getActivity() == null || !AnalysisUtil.isWritableLog()) {
            return false;
        }
        if (view.getId() == binding.txtVersion.getId()) {
            int size = (int) (AnalysisUtil.getLogFileSize() / 1024.0);
            String type = "KB";
            if (size > 1024) {
                type = "MB";
                size = size / 1024;
            }
            new AlertDialog.Builder(getActivity())
                    .setCancelable(true)
                    .setTitle(getString(R.string.viewLogFile))
                    .setMessage(getString(R.string.guideViewLogFile, size, type))
                    .setPositiveButton(getString(R.string.open), (dialog, which) -> openLogFile())
                    .setNegativeButton(getString(R.string.close), (dialog, which) -> {
                    })
                    .setNeutralButton(getString(R.string.delete), (dialog, which) -> AppExecutors.execute(AnalysisUtil::deleteLogFile))
                    .show();
            return true;
        }
        return false;
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

    AlertDialog permissionAlertDialog = null;

    private void permissionGrantedCheck() {
        if (getContext() == null) {
            return;
        }
        if (permissionAlertDialog != null && permissionAlertDialog.isShowing()) {
            return;
        }

        // notification listener
        Set<String> sets = NotificationManagerCompat.getEnabledListenerPackages(getContext());
        if (!sets.contains(getContext().getPackageName())) {
            permissionAlertDialog = new AlertDialog.Builder(getContext())
                    .setTitle(getString(R.string.grantPermission))
                    .setMessage(getString(R.string.guideGrantPermission))
                    // .setIcon(R.drawable.ic_launcher_background)
                    .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                                if (permissionAlertDialog != null) {
                                    permissionAlertDialog = null;
                                }
                                startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                            }
                    )
                    .setCancelable(false)
                    .show();
            return;
        }
        if (getContext() == null || getActivity() == null) {
            return;
        }
        // file write permission
        boolean granted = getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && getContext().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            permissionAlertDialog = new AlertDialog.Builder(getContext())
                    .setTitle(this.getString(R.string.grantPermission))
                    .setMessage(this.getString(R.string.guideGrantStoragePermission))
                    .setPositiveButton(this.getString(R.string.confirm), (dialog, which) -> {
                                getActivity().requestPermissions(new String[]
                                                {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                                        2);
                                if (permissionAlertDialog != null) {
                                    permissionAlertDialog = null;
                                }
                            }
                    )
                    .setCancelable(false)
                    .show();
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
        AppExecutors.execute(() -> AppUpdaterUtil.dialog(getActivity(), true));
    }

    private void onBtnPoiCacheClearClick() {
        if (binding != null && naviToTeslaService != null) {
            binding.btnPoiCacheClear.setEnabled(false);
            AppExecutors.execute(() -> {
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
                    && clipboard.getPrimaryClip().getItemAt(0) != null
                    && clipboard.getPrimaryClip().getItemAt(0).getText() != null) {
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


        AppExecutors.execute(() -> {
            if (getContext() != null && naviToTeslaService != null) {
                PreferencesUtil.clear(getContext());
                homeViewModel.getTokenLiveData().postValue(naviToTeslaService.getToken());
                TokenWorker.cancelBackgroundWork(getContext());
            }
        });

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
        if (refreshToken == null || refreshToken.length() == 0 || binding == null) {
            return;
        }

        binding.btnSave.setEnabled(false);
        binding.btnSave.setText(getString(R.string.checking));
        if (getActivity() != null) {
            View focusView = getActivity().getCurrentFocus();
            if (focusView != null) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(focusView.getWindowToken(), 0);
            }
        }

        final Activity context = getActivity();
        AppExecutors.execute(() -> {

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
                            binding.btnSave.setText(context.getString(R.string.save));
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
        homeViewModel.appVersion.postValue(AppUpdaterUtil.getCurrentVersion(this.getContext()));
    }

    private void updateLatestVersion() {
        homeViewModel.isUpdateAvailable.postValue(AppUpdaterUtil.isUpdateAvailable(this.getContext()));
    }

    private void renderVersion() {
        if (binding == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(homeViewModel.getAppVersion().getValue());
        if (homeViewModel.getIsUpdateAvailable().getValue() != null && homeViewModel.getIsUpdateAvailable().getValue()) {
            sb.append("\n").append("(").append(getString(R.string.updateAvailable)).append(")");
            binding.txtVersion.setTextColor(Color.RED);
        }
        binding.txtVersion.setText(sb.toString());
    }


    private void openLogFile() {
        if (getActivity() == null) {
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(getActivity(), BuildConfig.APPLICATION_ID + ".provider", new File(AnalysisUtil.getLogFilePath()));
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "plain/text");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            new AlertDialog.Builder(getActivity())
                    .setCancelable(true)
                    .setTitle(getString(R.string.requireLogViewApp))
                    .setMessage(getString(R.string.guideRequireLogViewApp))
                    .setPositiveButton(getString(R.string.install), (dialog, which) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=log viewer")));
                        } catch (ActivityNotFoundException anfe) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/search?q=log viewer")));
                        }
                    })
                    .setNegativeButton(getString(R.string.close), (dialog, which) -> {
                    })
                    .show();

        }
    }

    private boolean isTeslaAppInstalled() {
        if (getContext() == null) {
            return false;
        }
        if (BuildConfig.DEBUG) {
            return true;
        }
        try {
            getContext().getPackageManager().getPackageInfo("com.teslamotors.tesla", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // share mode
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        String shareMode;
        if (binding == null) {
            return;
        }
        if (binding.radioUsingTeslaApp.getId() == group.getCheckedRadioButtonId()) {
            shareMode = "app";
        } else {
            shareMode = "api";
        }
        Log.i("-----", shareMode);
        if (homeViewModel.getShareMode().getValue() == null && homeViewModel.getShareMode().getValue().equals(shareMode)) {
            return;
        }
        homeViewModel.getShareMode().postValue(shareMode);
        PreferencesUtil.put(getContext(), "shareMode", shareMode);
    }

    private void updateShareMode() {
        if (binding == null) {
            return;
        }
        boolean isAppInstalled = isTeslaAppInstalled();
        String shareMode = PreferencesUtil.getString(getContext(), "shareMode", isAppInstalled ? "app" : "api");
        homeViewModel.getShareMode().postValue(shareMode);

        if (shareMode.equals("api")) {
            binding.radioGroupShareMode.check(binding.radioUsingTeslaApi.getId());
        } else {
            binding.radioGroupShareMode.check(binding.radioUsingTeslaApp.getId());
            overlayPermissionGrantedCheck();
        }
    }

    private void onChangeTeslaAppInstalled(Boolean isInstalled) {
        if (binding == null) {
            return;
        }
        binding.radioUsingTeslaApp.setEnabled(isInstalled);
    }

    private void onChangedTeslaShareMode(String mode) {
        if (binding == null) {
            return;
        }
        boolean enableApiElement = !mode.equals("app");
        binding.txtRefreshToken.setEnabled(enableApiElement);
        binding.btnPaste.setEnabled(enableApiElement);
        binding.btnSave.setEnabled(enableApiElement);
        binding.vehicleSelector.setEnabled(enableApiElement);
        binding.btnTokenClear.setEnabled(enableApiElement);

        if (mode.equals("app")) {
            overlayPermissionGrantedCheck();
        }
    }

    private synchronized void overlayPermissionGrantedCheck() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (getContext() != null && !Settings.canDrawOverlays(getContext())
                        && (permissionAlertDialog == null || !permissionAlertDialog.isShowing())
                ) {
                    permissionAlertDialog = new AlertDialog.Builder(getContext())
                            .setTitle(getString(R.string.grantPermission))
                            .setMessage(getString(R.string.guideGrantOverlayPermission))
                            .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                                        if (permissionAlertDialog != null) {
                                            permissionAlertDialog = null;
                                        }
                                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + getContext().getPackageName())));
                                    }
                            )
                            .setNegativeButton(getString(R.string.deny), (dialog, which) -> {
                                if (permissionAlertDialog != null) {
                                    permissionAlertDialog = null;
                                }
                                if (binding != null) {
                                    binding.radioGroupShareMode.check(binding.radioUsingTeslaApi.getId());
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        }
    }
}