package me.zipi.navitotesla.ui.setting;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import me.zipi.navitotesla.AppExecutors;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.databinding.FragmentSettingsBinding;
import me.zipi.navitotesla.util.EnablerUtil;


public class SettingFragment extends Fragment implements View.OnClickListener, RadioGroup.OnCheckedChangeListener {


    private SettingViewModel settingViewModel;
    @Nullable
    private FragmentSettingsBinding binding;
    @Nullable
    private ConditionRecyclerAdapter conditionRecyclerAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        settingViewModel =
                new ViewModelProvider(this).get(SettingViewModel.class);

        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();


        binding.btnBluetoothAdd.setOnClickListener(this);
        binding.btnConditionHelp.setOnClickListener(this);
        binding.btnAppEnableHelp.setOnClickListener(this);

        binding.radioGroupAppEnable.setOnCheckedChangeListener(this);
        binding.radioGroupConditionEnable.setOnCheckedChangeListener(this);


        settingViewModel.getIsConditionEnabled().observe(getViewLifecycleOwner(), this::onChangedConditionEnabled);
        settingViewModel.getIsAppEnabled().observe(getViewLifecycleOwner(), this::onChangedAppEnabled);


        conditionRecyclerAdapter = new ConditionRecyclerAdapter(position -> {
            if (getActivity() == null || getContext() == null) {
                return;
            }

            new AlertDialog.Builder(getActivity())
                    .setCancelable(true)
                    .setTitle(getString(R.string.removeCondition))
                    .setMessage(getString(R.string.dialogRemoveCondition))
                    .setPositiveButton(getString(R.string.delete), (dialog, which) -> removeBluetoothDevice(position))
                    .setNegativeButton(getString(R.string.cancel), (dialog, which) -> {
                    })
                    .show();
        });
        binding.recylerBluetooth.setAdapter(conditionRecyclerAdapter);
        binding.recylerBluetooth.setLayoutManager(new LinearLayoutManager(getContext()));
        settingViewModel.getBluetoothConditions().observe(getViewLifecycleOwner(), (items) -> {
            conditionRecyclerAdapter.setItems(items);
        });
        return root;
    }

    private void removeBluetoothDevice(int position) {
        AppExecutors.execute(() -> {
            if (getContext() == null || settingViewModel.getBluetoothConditions().getValue() == null) {
                return;
            }
            String bluetooth = settingViewModel.getBluetoothConditions().getValue().get(position);
            EnablerUtil.removeBluetoothCondition(getContext(), bluetooth);
            updateConditions();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        updateConditions();
    }

    private void updateConditions() {
        AppExecutors.execute(() -> {
            if (getContext() != null && binding != null && getActivity() != null) {
                boolean appEnabled = EnablerUtil.getAppEnabled(getContext());
                boolean conditionEnabled = EnablerUtil.getConditionEnabled(getContext());
                if (getActivity() == null) {
                    return;
                }

                getActivity().runOnUiThread(() -> {
                    if (binding == null) {
                        return;
                    }
                    binding.radioGroupAppEnable.check(appEnabled
                            ? binding.radioAppEnable.getId() : binding.radioAppDisable.getId());
                    binding.radioGroupConditionEnable.check(conditionEnabled
                            ? binding.radioConditionEnable.getId() : binding.radioConditionDisable.getId());
                });
            }
        });
        AppExecutors.execute(()->  settingViewModel.getBluetoothConditions().postValue(EnablerUtil.listBluetoothCondition(getContext())));
        AppExecutors.execute(()->  settingViewModel.getIsAppEnabled().postValue(EnablerUtil.getAppEnabled(getContext())));
        AppExecutors.execute(()->  settingViewModel.getIsConditionEnabled().postValue(EnablerUtil.getConditionEnabled(getContext())));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        settingViewModel.clearObserve(getViewLifecycleOwner());
    }

    @Override
    public void onClick(View v) {
        if (binding == null || getActivity() == null || getContext() == null) {
            return;
        }
        if (v.getId() == binding.btnAppEnableHelp.getId()) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideAppEnable))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    })
                    .create().show();
        } else if (v.getId() == binding.btnConditionHelp.getId()) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideCondition))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm), (dialog, which) -> {
                    })
                    .create().show();
        } else if (v.getId() == binding.btnBluetoothAdd.getId()) {
            addBluetooth();
        }
    }


    private void addBluetooth() {
        Activity activity = getActivity();
        if (activity == null || binding == null || !checkBluetoothPermission()) {
            return;
        }

        View dialogView = ((LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.custom_spinner_dialog_layout, null);
        final Spinner dialogSpinner = (Spinner) dialogView.findViewById(R.id.spinnerDialog);


        List<String> pairedDevices = EnablerUtil.getPairedBluetooth(getContext());
        dialogSpinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, pairedDevices));

        new AlertDialog.Builder(activity)
                .setCancelable(true)
                .setTitle(activity.getString(R.string.titleAddCondition))
                .setMessage(activity.getString(R.string.guideAddCondition))
                .setPositiveButton(activity.getString(R.string.save), (dialog, which) -> {
                    if (dialog == null || dialogSpinner.getSelectedItem() == null) {
                        return;
                    }
                    final String selectedDevice = dialogSpinner.getSelectedItem().toString();
                    AppExecutors.execute(() -> {
                        if (getContext() != null) {
                            EnablerUtil.addBluetoothCondition(getContext(), selectedDevice);
                            settingViewModel.getBluetoothConditions().postValue(EnablerUtil.listBluetoothCondition(getContext()));
                        }
                    });
                })
                .setNegativeButton(activity.getString(R.string.close), (dialog, which) -> {
                })
                .setView(dialogView)
                .show();
    }


    private boolean checkBluetoothPermission() {
        if (getContext() == null || getActivity() == null) {
            return false;
        }
        final String permission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? Manifest.permission.BLUETOOTH_CONNECT : Manifest.permission.BLUETOOTH;

        boolean granted = ActivityCompat.checkSelfPermission(getContext(), permission) == PackageManager.PERMISSION_GRANTED;

        if (!granted) {
            new AlertDialog.Builder(getContext())
                    .setTitle(this.getString(R.string.grantPermission))
                    .setMessage(this.getString(R.string.guideGrantBluetoothPermission))
                    .setPositiveButton(this.getString(R.string.confirm), (dialog, which) -> getActivity().requestPermissions(new String[]
                                    {permission},
                            2)
                    )
                    .setCancelable(false)
                    .show();
        }
        return granted;
    }


    private void onChangedAppEnabled(Boolean enabled) {
        AppExecutors.execute(() -> {
            if (getContext() != null) {
                EnablerUtil.setAppEnabled(getContext(), enabled);
            }
        });
    }

    private void onChangedConditionEnabled(Boolean enabled) {
        AppExecutors.execute(() -> {
            if (getContext() != null) {
                EnablerUtil.setConditionEnabled(getContext(), enabled);
            }
        });
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (settingViewModel == null) {
            return;
        }
        if (checkedId == R.id.radioAppEnable) {
            if (settingViewModel.getIsAppEnabled().getValue() != null && !settingViewModel.getIsAppEnabled().getValue()) {
                settingViewModel.getIsAppEnabled().postValue(true);
            }
        } else if (checkedId == R.id.radioAppDisable) {
            if (settingViewModel.getIsAppEnabled().getValue() != null && settingViewModel.getIsAppEnabled().getValue()) {
                settingViewModel.getIsAppEnabled().postValue(false);
            }
        } else if (checkedId == R.id.radioConditionEnable) {
            if (settingViewModel.getIsConditionEnabled().getValue() != null && !settingViewModel.getIsConditionEnabled().getValue()) {
                settingViewModel.getIsConditionEnabled().postValue(true);
            }
        } else if (checkedId == R.id.radioConditionDisable) {
            if (settingViewModel.getIsConditionEnabled().getValue() != null && settingViewModel.getIsConditionEnabled().getValue()) {
                settingViewModel.getIsConditionEnabled().postValue(false);
            }
        }
    }
}