package me.zipi.navitotesla.ui.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
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
import me.zipi.navitotesla.background.TokenWorker;
import me.zipi.navitotesla.databinding.FragmentHomeBinding;
import me.zipi.navitotesla.model.Token;
import me.zipi.navitotesla.model.Vehicle;
import me.zipi.navitotesla.service.NaviToTeslaService;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.AppUpdaterUtil;
import me.zipi.navitotesla.util.PreferencesMigrationUtil;
import me.zipi.navitotesla.util.PreferencesUtil;

public class HomeFragment extends Fragment implements AdapterView.OnItemSelectedListener, View.OnClickListener, View.OnLongClickListener {

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
        binding.txtVersion.setOnLongClickListener(this);

        return root;
    }

    @Override
    public void onResume() {
        super.onResume();

        permissionGrantedCheck();

        AppExecutors.execute(this::updateToken);
        AppExecutors.execute(() -> AppUpdaterUtil.dialog(getActivity()));
        AppExecutors.execute(this::updateVersion);


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
        if (binding == null || getActivity() == null) {
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
                    .setTitle("로그파일보기")
                    .setMessage(String.format("로그파일(%s%s)를 확인하시겠습니까?", size, type))
                    .setPositiveButton("열기", (dialog, which) -> openLogFile())
                    .setNegativeButton("닫기", (dialog, which) -> {
                    })
                    .setNeutralButton("삭제", (dialog, which) -> AppExecutors.execute(AnalysisUtil::deleteLogFile))
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
        binding.btnSave.setText("확인중");
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
        AppExecutors.execute(() -> {
            homeViewModel.appVersion.postValue(AppUpdaterUtil.getCurrentVersion(this.getContext()));
            homeViewModel.isUpdateAvailable.postValue(AppUpdaterUtil.isUpdateAvailable(this.getContext()));
        });

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
                    .setTitle("앱 설치 필요")
                    .setMessage("로그 파일을 열 수 있는 앱이 없습니다.\n스토어에서 찾아 설치하시겠습니까?")
                    .setPositiveButton("설치", (dialog, which) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=log viewer")));
                        } catch (ActivityNotFoundException anfe) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/search?q=log viewer")));
                        }
                    })
                    .setNegativeButton("닫기", (dialog, which) -> {
                    })
                    .show();

        }
    }

}