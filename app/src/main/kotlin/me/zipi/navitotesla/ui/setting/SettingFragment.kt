package me.zipi.navitotesla.ui.setting

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.databinding.FragmentSettingsBinding
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.ui.setting.ConditionRecyclerAdapter.OnDeleteButtonClicked
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.EnablerUtil
import me.zipi.navitotesla.util.PreferencesUtil
import java.io.File

class SettingFragment :
    Fragment(),
    View.OnClickListener,
    RadioGroup.OnCheckedChangeListener {
    private lateinit var settingViewModel: SettingViewModel
    private lateinit var binding: FragmentSettingsBinding
    private lateinit var conditionRecyclerAdapter: ConditionRecyclerAdapter
    private var isDuplicatePoiRadioInitializing = false
    private var diagnosticsUserToggled = false
    private var diagnosticsExpanded = false

    private val sendModeValues = listOf("road", "jibun", "name")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        settingViewModel = ViewModelProvider(this)[SettingViewModel::class.java]
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.btnBluetoothAdd.setOnClickListener(this)
        binding.btnConditionHelp.setOnClickListener(this)
        binding.btnAppEnableHelp.setOnClickListener(this)
        binding.btnAccEnableHelp.setOnClickListener(this)
        binding.btnDuplicatePoiHelp.setOnClickListener(this)
        binding.radioGroupAppEnable.setOnCheckedChangeListener(this)
        binding.radioGroupConditionEnable.setOnCheckedChangeListener(this)
        binding.radioGroupAccEnable.setOnCheckedChangeListener(this)
        settingViewModel.isConditionEnabled
            .observe(viewLifecycleOwner) { enabled: Boolean -> onChangedConditionEnabled(enabled) }
        settingViewModel.isAppEnabled
            .observe(viewLifecycleOwner) { enabled: Boolean -> onChangedAppEnabled(enabled) }

        conditionRecyclerAdapter =
            ConditionRecyclerAdapter(
                object : OnDeleteButtonClicked {
                    override fun onClick(position: Int) {
                        activity?.run {
                            AlertDialog
                                .Builder(this)
                                .setCancelable(true)
                                .setTitle(getString(R.string.removeCondition))
                                .setMessage(getString(R.string.dialogRemoveCondition))
                                .setPositiveButton(getString(R.string.delete)) { _: DialogInterface?, _: Int ->
                                    removeBluetoothDevice(
                                        position,
                                    )
                                }.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
                                .show()
                        }
                    }
                },
            )
        binding.recylerBluetooth.adapter = conditionRecyclerAdapter
        binding.recylerBluetooth.layoutManager = LinearLayoutManager(context)
        settingViewModel.bluetoothConditions
            .observe(viewLifecycleOwner) { items ->
                conditionRecyclerAdapter.setItems(items)
                binding.textBluetoothEmpty.visibility = if (items.isNullOrEmpty()) View.VISIBLE else View.GONE
            }
        binding.radioGroupDuplicatePoiSelection.setOnCheckedChangeListener(this)
        setupSendModeSpinners()
        return root
    }

    private fun setupSendModeSpinners() {
        bindSendModeSpinner(binding.spinnerDefaultSendMode, "defaultSendMode")
        bindSendModeSpinner(binding.spinnerFallbackSendMode, "fallbackSendMode")
    }

    private fun bindSendModeSpinner(
        spinner: Spinner,
        prefKey: String,
    ) {
        val adapter =
            ArrayAdapter
                .createFromResource(
                    requireContext(),
                    R.array.sendModeEntries,
                    android.R.layout.simple_spinner_item,
                ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spinner.adapter = adapter
        // 0번 인덱스를 기본값으로 미리 선택. 비동기 로드 완료 후 실제 저장값으로 교체된다.
        // listener 는 setSelection 이후 attach 하므로 이 시점 callback 은 발생하지 않는다.
        spinner.setSelection(0, false)

        lifecycleScope.launch {
            val saved = PreferencesUtil.getString(prefKey, "road") ?: "road"
            val idx = sendModeValues.indexOf(saved).takeIf { it >= 0 } ?: 0
            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext
                spinner.setSelection(idx, false)
                // setSelection 이후 listener attach: programmatic selection 으로 인한 write-back race 방지
                spinner.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(
                            parent: AdapterView<*>?,
                            view: View?,
                            position: Int,
                            id: Long,
                        ) {
                            val value = sendModeValues.getOrNull(position) ?: return
                            lifecycleScope.launch { PreferencesUtil.put(prefKey, value) }
                        }

                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                    }
            }
        }
    }

    private fun removeBluetoothDevice(position: Int) {
        if (context == null || settingViewModel.bluetoothConditions.value == null) {
            return
        }
        settingViewModel.bluetoothConditions.value!![position]
            .run { EnablerUtil.removeBluetoothCondition(this) }

        updateConditions()
    }

    override fun onResume() {
        super.onResume()
        updateConditions()
        updateDiagnostics()
    }

    private fun updateDiagnostics() {
        val ctx = context ?: return
        val notiOk = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val listenerOk =
            NotificationManagerCompat
                .getEnabledListenerPackages(ctx)
                .contains(ctx.packageName)
        val overlayOk = Settings.canDrawOverlays(ctx)
        bindDiagnosticRow(
            binding.diagRowNotification,
            R.string.diagPermNotification,
            R.string.guideGrantNotificationPermission,
            notiOk,
        ) { openAppNotificationSettings() }
        bindDiagnosticRow(
            binding.diagRowNotificationListener,
            R.string.diagPermNotificationListener,
            R.string.guideGrantPermission,
            listenerOk,
        ) { openNotificationListenerSettings() }
        bindDiagnosticRow(
            binding.diagRowOverlay,
            R.string.diagPermOverlay,
            R.string.guideGrantOverlayPermission,
            overlayOk,
        ) { openOverlaySettings() }

        bindLogRow()

        val anyFail = !(notiOk && listenerOk && overlayOk)
        if (!diagnosticsUserToggled) {
            applyDiagnosticsExpanded(anyFail)
        }
        binding.diagHeader.setOnClickListener {
            diagnosticsUserToggled = true
            applyDiagnosticsExpanded(!diagnosticsExpanded)
        }
    }

    private fun applyDiagnosticsExpanded(expanded: Boolean) {
        diagnosticsExpanded = expanded
        binding.diagContent.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.diagExpandIcon.rotation = if (expanded) 180f else 0f
    }

    private fun bindLogRow() {
        val ctx = context ?: return
        val row = binding.diagRowLogFile
        if (!AnalysisUtil.isWritableLog) {
            row.root.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) { AnalysisUtil.logFileSize }
            if (size <= 0L) {
                row.root.visibility = View.GONE
                return@launch
            }
            row.root.visibility = View.VISIBLE
            row.logSize.text = Formatter.formatShortFileSize(ctx, size)
            row.logOpenButton.setOnClickListener { openLogFile() }
        }
    }

    private fun openLogFile() {
        val activity = activity ?: return
        if (!AnalysisUtil.isWritableLog) return
        try {
            val uri =
                FileProvider.getUriForFile(
                    activity,
                    "${BuildConfig.APPLICATION_ID}.provider",
                    File(AnalysisUtil.logFilePath),
                )
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            AlertDialog
                .Builder(activity)
                .setCancelable(true)
                .setTitle(getString(R.string.requireLogViewApp))
                .setMessage(getString(R.string.guideRequireLogViewApp))
                .setPositiveButton(getString(R.string.install)) { _: DialogInterface?, _: Int ->
                    try {
                        startActivity(
                            Intent(Intent.ACTION_VIEW, "market://search?q=log viewer".toUri()),
                        )
                    } catch (_: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/search?q=log viewer".toUri(),
                            ),
                        )
                    }
                }.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
                .show()
        }
    }

    private fun bindDiagnosticRow(
        row: me.zipi.navitotesla.databinding.ViewDiagnosticRowBinding,
        labelRes: Int,
        guideRes: Int,
        ok: Boolean,
        onFix: () -> Unit,
    ) {
        row.diagLabel.setText(labelRes)
        row.diagIcon.setImageResource(if (ok) R.drawable.ic_check_circle_20 else R.drawable.ic_warning_20)
        row.diagStatusOk.visibility = if (ok) View.VISIBLE else View.GONE
        row.diagFixButton.visibility = if (ok) View.GONE else View.VISIBLE
        row.diagFixButton.setOnClickListener { onFix() }
        row.diagInfoButton.setOnClickListener { showDiagnosticGuide(labelRes, guideRes) }
    }

    private fun showDiagnosticGuide(
        titleRes: Int,
        messageRes: Int,
    ) {
        if (activity == null) return
        AlertDialog
            .Builder(requireActivity())
            .setTitle(getString(titleRes))
            .setMessage(getString(messageRes))
            .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
            .setCancelable(true)
            .show()
    }

    private fun openAppNotificationSettings() {
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", requireContext().packageName, null))
            }
        runCatching { startActivity(intent) }
    }

    private fun openNotificationListenerSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    }

    private fun openOverlaySettings() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${requireContext().packageName}"),
            )
        runCatching { startActivity(intent) }
    }

    private fun updateConditions() =
        lifecycleScope.launch {
            if (context != null && activity != null) {
                val appEnabled = context?.let { EnablerUtil.getAppEnabled() } ?: true
                val conditionEnabled = context?.let { EnablerUtil.getConditionEnabled() } ?: false
                val accEnabled: Boolean =
                    context?.let {
                        NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(context)
                    } ?: false

                withContext(Dispatchers.Main) {
                    binding.radioGroupAppEnable.check(if (appEnabled) binding.radioAppEnable.id else binding.radioAppDisable.id)
                    binding.radioGroupConditionEnable.check(
                        if (conditionEnabled) binding.radioConditionEnable.id else binding.radioConditionDisable.id,
                    )
                    if (accEnabled) {
                        binding.radioAccEnable.isChecked = true
                    } else {
                        binding.radioAccDisable.isChecked = true
                    }
                }
            }

            launch {
                context?.run {
                    settingViewModel.bluetoothConditions.postValue(
                        EnablerUtil.listBluetoothCondition(),
                    )
                }
            }
            launch {
                context?.run {
                    settingViewModel.isAppEnabled.postValue(
                        EnablerUtil.getAppEnabled(),
                    )
                }
            }
            launch {
                context?.run {
                    settingViewModel.isConditionEnabled.postValue(
                        EnablerUtil.getConditionEnabled(),
                    )
                }
            }
            launch {
                context?.run {
                    val enabled = PreferencesUtil.getBoolean("duplicatePoiSelection", true)
                    withContext(Dispatchers.Main) {
                        isDuplicatePoiRadioInitializing = true
                        binding.radioGroupDuplicatePoiSelection.check(
                            if (enabled) binding.radioDuplicatePoiShowPopup.id else binding.radioDuplicatePoiIgnore.id,
                        )
                        isDuplicatePoiRadioInitializing = false
                    }
                }
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
                AlertDialog
                    .Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideAppEnable))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
            }

            binding.btnDuplicatePoiHelp.id -> {
                AlertDialog
                    .Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideDuplicatePoiSelection))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
            }

            binding.btnConditionHelp.id -> {
                AlertDialog
                    .Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.guideCondition))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
            }

            binding.btnAccEnableHelp.id -> {
                AlertDialog
                    .Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.accessibility_description))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
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
                null,
            )
        val dialogSpinner = dialogView.findViewById<View>(R.id.spinnerDialog) as Spinner
        val pairedDevices = EnablerUtil.getPairedBluetooth(context)
        dialogSpinner.adapter =
            ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                pairedDevices,
            )
        AlertDialog
            .Builder(activity)
            .setCancelable(true)
            .setTitle(activity.getString(R.string.titleAddCondition))
            .setMessage(activity.getString(R.string.guideAddCondition))
            .setPositiveButton(activity.getString(R.string.save)) { dialog: DialogInterface?, _: Int ->
                if (dialog == null || dialogSpinner.selectedItem == null) {
                    return@setPositiveButton
                }
                val selectedDevice = dialogSpinner.selectedItem.toString()
                lifecycleScope.launch {
                    if (context != null) {
                        EnablerUtil.addBluetoothCondition(selectedDevice)
                        settingViewModel.bluetoothConditions.postValue(
                            EnablerUtil.listBluetoothCondition(),
                        )
                    }
                }
            }.setNegativeButton(activity.getString(R.string.close)) { _: DialogInterface?, _: Int -> }
            .setView(dialogView)
            .show()
    }

    private fun checkBluetoothPermission(): Boolean {
        if (context == null || activity == null) {
            return false
        }
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        val granted =
            ActivityCompat.checkSelfPermission(
                requireContext(),
                permission,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            AlertDialog
                .Builder(requireContext())
                .setTitle(this.getString(R.string.grantPermission))
                .setMessage(this.getString(R.string.guideGrantBluetoothPermission))
                .setPositiveButton(
                    this.getString(R.string.confirm),
                ) { _: DialogInterface?, _: Int ->
                    requireActivity().requestPermissions(
                        arrayOf(permission),
                        2,
                    )
                }.setCancelable(false)
                .show()
        }
        return granted
    }

    private fun onChangedAppEnabled(enabled: Boolean) {
        lifecycleScope.launch {
            if (context != null) {
                EnablerUtil.setAppEnabled(enabled)
            }
        }
    }

    private fun onChangedConditionEnabled(enabled: Boolean) {
        binding.cardBluetooth.visibility = if (enabled) View.VISIBLE else View.GONE
        lifecycleScope.launch {
            if (context != null) {
                EnablerUtil.setConditionEnabled(enabled)
            }
        }
    }

    override fun onCheckedChanged(
        group: RadioGroup,
        checkedId: Int,
    ) {
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
            if (activity != null &&
                !NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(
                    activity,
                )
            ) {
                showAccessibilityConsentDialog(onDeny = { setAccRadio(false) })
            }
        } else if (checkedId == R.id.radioDuplicatePoiShowPopup) {
            if (!isDuplicatePoiRadioInitializing) {
                if (context != null && !Settings.canDrawOverlays(requireContext())) {
                    showOverlayPermissionDialog()
                }
                lifecycleScope.launch {
                    PreferencesUtil.put("duplicatePoiSelection", true)
                }
            }
        } else if (checkedId == R.id.radioDuplicatePoiIgnore) {
            if (!isDuplicatePoiRadioInitializing) {
                lifecycleScope.launch {
                    PreferencesUtil.put("duplicatePoiSelection", false)
                }
            }
        } else if (checkedId == R.id.radioAccDisable) {
            if (activity != null &&
                NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(
                    activity,
                )
            ) {
                AlertDialog
                    .Builder(requireActivity())
                    .setTitle(getString(R.string.guide))
                    .setMessage(getString(R.string.disableAccessibility))
                    .setCancelable(true)
                    .setPositiveButton(getString(R.string.title_setting)) { _: DialogInterface?, _: Int -> openAccessibilitySettings() }
                    .setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int ->
                        setAccRadio(
                            true,
                        )
                    }.create()
                    .show()
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

    private fun showAccessibilityConsentDialog(onDeny: () -> Unit = {}) {
        if (activity == null) return
        AlertDialog
            .Builder(requireActivity())
            .setTitle(getString(R.string.guide))
            .setMessage(getString(R.string.accessibility_description))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.allow)) { _: DialogInterface?, _: Int ->
                openAccessibilitySettings()
            }.setNegativeButton(getString(R.string.deny)) { _: DialogInterface?, _: Int ->
                onDeny()
            }.create()
            .show()
    }

    private fun showOverlayPermissionDialog() {
        if (activity == null) return
        AlertDialog
            .Builder(requireActivity())
            .setTitle(getString(R.string.grantPermission))
            .setMessage(getString(R.string.guideGrantOverlayPermissionForSelection))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.allow)) { _: DialogInterface?, _: Int ->
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${requireContext().packageName}"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }.setNegativeButton(getString(R.string.deny)) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    PreferencesUtil.put("duplicatePoiSelection", false)
                    withContext(Dispatchers.Main) {
                        isDuplicatePoiRadioInitializing = true
                        binding.radioGroupDuplicatePoiSelection.check(binding.radioDuplicatePoiIgnore.id)
                        isDuplicatePoiRadioInitializing = false
                    }
                }
            }.create()
            .show()
    }
}
