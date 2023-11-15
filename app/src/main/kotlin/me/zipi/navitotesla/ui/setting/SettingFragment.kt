package me.zipi.navitotesla.ui.setting

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import me.zipi.navitotesla.AppExecutors
import me.zipi.navitotesla.R
import me.zipi.navitotesla.databinding.FragmentSettingsBinding
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.ui.setting.ConditionRecyclerAdapter.OnDeleteButtonClicked
import me.zipi.navitotesla.util.EnablerUtil

class SettingFragment : Fragment(), View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private lateinit var settingViewModel: SettingViewModel
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var conditionRecyclerAdapter: ConditionRecyclerAdapter
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        settingViewModel = ViewModelProvider(this).get(SettingViewModel::class.java)
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.btnBluetoothAdd.setOnClickListener(this)
        binding.btnConditionHelp.setOnClickListener(this)
        binding.btnAppEnableHelp.setOnClickListener(this)
        binding.btnAccEnableHelp.setOnClickListener(this)
        binding.radioGroupAppEnable.setOnCheckedChangeListener(this)
        binding.radioGroupConditionEnable.setOnCheckedChangeListener(this)
        binding.radioGroupAccEnable.setOnCheckedChangeListener(this)
        settingViewModel.isConditionEnabled
            .observe(viewLifecycleOwner) { enabled: Boolean -> onChangedConditionEnabled(enabled) }
        settingViewModel.isAppEnabled
            .observe(viewLifecycleOwner) { enabled: Boolean -> onChangedAppEnabled(enabled) }

        conditionRecyclerAdapter = ConditionRecyclerAdapter(object : OnDeleteButtonClicked {
            override fun onClick(position: Int) {
                if (activity == null || context == null) {
                    return
                }
                AlertDialog.Builder(requireActivity())
                    .setCancelable(true)
                    .setTitle(getString(R.string.removeCondition))
                    .setMessage(getString(R.string.dialogRemoveCondition))
                    .setPositiveButton(getString(R.string.delete)) { dialog: DialogInterface?, which: Int ->
                        removeBluetoothDevice(
                            position
                        )
                    }
                    .setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface?, which: Int -> }
                    .show()
            }
        })
        binding.recylerBluetooth.adapter = conditionRecyclerAdapter
        binding.recylerBluetooth.layoutManager = LinearLayoutManager(context)
        settingViewModel.bluetoothConditions
            .observe(viewLifecycleOwner) { items -> conditionRecyclerAdapter.setItems(items) }
        return root
    }

    private fun removeBluetoothDevice(position: Int) {
        AppExecutors.execute {
            if (context == null || settingViewModel.bluetoothConditions.value == null) {
                return@execute
            }
            val bluetooth: String =
                settingViewModel.bluetoothConditions.value!![position]
            EnablerUtil.removeBluetoothCondition(requireContext(), bluetooth)
            updateConditions()
        }
    }

    override fun onResume() {
        super.onResume()
        updateConditions()
    }

    private fun updateConditions() {
        AppExecutors.execute {
            if (context != null && activity != null) {
                val appEnabled = EnablerUtil.getAppEnabled(requireContext())
                val conditionEnabled = EnablerUtil.getConditionEnabled(requireContext())
                val accEnabled: Boolean =
                    NaviToTeslaAccessibilityService.Companion.isAccessibilityServiceEnabled(
                        context
                    )
                if (activity == null) {
                    return@execute
                }
                requireActivity().runOnUiThread {
                    binding.radioGroupAppEnable.check(if (appEnabled) binding.radioAppEnable.id else binding.radioAppDisable.id)
                    binding.radioGroupConditionEnable.check(if (conditionEnabled) binding.radioConditionEnable.id else binding.radioConditionDisable.id)
                    if (accEnabled) {
                        binding.radioAccEnable.isChecked = true
                    } else {
                        binding.radioAccDisable.isChecked = true
                    }
                }
            }
        }
        AppExecutors.execute {
            settingViewModel.bluetoothConditions?.postValue(
                EnablerUtil.listBluetoothCondition(requireContext())
            )
        }
        AppExecutors.execute {
            settingViewModel.isAppEnabled?.postValue(
                EnablerUtil.getAppEnabled(requireContext())
            )
        }
        AppExecutors.execute {
            settingViewModel.isConditionEnabled?.postValue(
                EnablerUtil.getConditionEnabled(requireContext())
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        settingViewModel.clearObserve(viewLifecycleOwner)
    }

    override fun onClick(v: View) {
        if (activity == null || context == null) {
            return
        }
        when (v.id) {
            binding.btnAppEnableHelp.id -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideAppEnable))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { dialog: DialogInterface?, which: Int -> }
                    .create().show()
            }

            binding.btnConditionHelp.id -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideCondition))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { dialog: DialogInterface?, which: Int -> }
                    .create().show()
            }

            binding.btnAccEnableHelp.id -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.accessibility_description))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { dialog: DialogInterface?, which: Int -> }
                    .create().show()
            }

            binding.btnBluetoothAdd.id -> {
                addBluetooth()
            }
        }
    }

    private fun addBluetooth() {
        val activity: Activity? = activity
        if (activity == null || !checkBluetoothPermission()) {
            return
        }
        val dialogView =
            (activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(
                R.layout.custom_spinner_dialog_layout,
                null
            )
        val dialogSpinner = dialogView.findViewById<View>(R.id.spinnerDialog) as Spinner
        val pairedDevices = EnablerUtil.getPairedBluetooth(context)
        dialogSpinner.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                pairedDevices
            )
        AlertDialog.Builder(activity)
            .setCancelable(true)
            .setTitle(activity.getString(R.string.titleAddCondition))
            .setMessage(activity.getString(R.string.guideAddCondition))
            .setPositiveButton(activity.getString(R.string.save)) { dialog: DialogInterface?, _: Int ->
                if (dialog == null || dialogSpinner.selectedItem == null) {
                    return@setPositiveButton
                }
                val selectedDevice = dialogSpinner.selectedItem.toString()
                AppExecutors.execute {
                    if (context != null) {
                        EnablerUtil.addBluetoothCondition(requireContext(), selectedDevice)
                        settingViewModel.bluetoothConditions.postValue(
                            EnablerUtil.listBluetoothCondition(requireContext())
                        )
                    }
                }
            }
            .setNegativeButton(activity.getString(R.string.close)) { _: DialogInterface?, _: Int -> }
            .setView(dialogView)
            .show()
    }

    private fun checkBluetoothPermission(): Boolean {
        if (context == null || activity == null) {
            return false
        }
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        val granted = ActivityCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            AlertDialog.Builder(requireContext())
                .setTitle(this.getString(R.string.grantPermission))
                .setMessage(this.getString(R.string.guideGrantBluetoothPermission))
                .setPositiveButton(
                    this.getString(R.string.confirm)
                ) { dialog: DialogInterface?, which: Int ->
                    requireActivity().requestPermissions(
                        arrayOf(permission),
                        2
                    )
                }
                .setCancelable(false)
                .show()
        }
        return granted
    }

    private fun onChangedAppEnabled(enabled: Boolean) {
        AppExecutors.execute {
            if (context != null) {
                EnablerUtil.setAppEnabled(requireContext(), enabled)
            }
        }
    }

    private fun onChangedConditionEnabled(enabled: Boolean) {
        AppExecutors.execute {
            if (context != null) {
                EnablerUtil.setConditionEnabled(requireContext(), enabled)
            }
        }
    }

    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        if (settingViewModel == null) {
            return
        }
        if (checkedId == R.id.radioAppEnable) {
            if (settingViewModel.isAppEnabled.value == false) {
                settingViewModel.isAppEnabled.postValue(true)
            }
        } else if (checkedId == R.id.radioAppDisable) {
            if (settingViewModel.isAppEnabled.value == true) {
                settingViewModel.isAppEnabled.postValue(false)
            }
        } else if (checkedId == R.id.radioConditionEnable) {
            if (settingViewModel.isConditionEnabled.value == false) {
                settingViewModel.isConditionEnabled.postValue(true)
            }
        } else if (checkedId == R.id.radioConditionDisable) {
            if (settingViewModel.isConditionEnabled.value == true) {
                settingViewModel.isConditionEnabled.postValue(false)
            }
        } else if (checkedId == R.id.radioAccEnable) {
            if (activity != null && !NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(
                    activity
                )
            ) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.accessibility_description))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.allow)) { dialog: DialogInterface?, which: Int -> openAccessibilitySettings() }
                    .setNegativeButton(getString(R.string.deny)) { dialog: DialogInterface?, which: Int ->
                        setAccRadio(
                            false
                        )
                    }
                    .create().show()
            }
        } else if (checkedId == R.id.radioAccDisable) {
            if (activity != null && NaviToTeslaAccessibilityService.Companion.isAccessibilityServiceEnabled(
                    activity
                )
            ) {
                AlertDialog.Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.disableAccessibility))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.title_setting)) { dialog: DialogInterface?, which: Int -> openAccessibilitySettings() }
                    .setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface?, which: Int ->
                        setAccRadio(
                            true
                        )
                    }
                    .create().show()
            }
        }
    }

    private fun setAccRadio(enable: Boolean) {
        if (activity != null) {
            requireActivity().runOnUiThread {
                if (enable) {
                    binding.radioAccEnable.isChecked = true
                } else {
                    binding.radioAccDisable.isChecked = true
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}