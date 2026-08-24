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
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.databinding.FragmentSettingsBinding
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.ui.setting.ConditionRecyclerAdapter.OnDeleteButtonClicked
import me.zipi.navitotesla.util.AccessibilityDisclosure
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
    private var diagnosticsUserToggled = false
    private var diagnosticsExpanded = false
    private var diagnosticsAnimReady = false
    private var conditionAnimReady = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        settingViewModel = ViewModelProvider(this)[SettingViewModel::class.java]
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        binding.btnBluetoothAdd.setOnClickListener(this)
        binding.btnAccEnableHelp.setOnClickListener(this)
        binding.radioGroupAppEnable.setOnCheckedChangeListener(this)
        binding.radioGroupConditionEnable.setOnCheckedChangeListener(this)
        binding.radioAccEnable.setOnClickListener { onAccEnableClicked() }
        binding.radioAccDisable.setOnClickListener { revokeAccessibilityConsent() }
        binding.btnAccShowGuideAgain.setOnClickListener { showGuideAgain() }
        binding.btnAccShowGuideAgain.visibility =
            if (AccessibilityDisclosure.isGuideHiddenSync()) View.VISIBLE else View.GONE
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
        binding.radioDuplicatePoiShowPopup.setOnClickListener { onDuplicatePoiSelected(true) }
        binding.radioDuplicatePoiIgnore.setOnClickListener { onDuplicatePoiSelected(false) }
        return root
    }

    private fun removeBluetoothDevice(position: Int) {
        val name = settingViewModel.bluetoothConditions.value?.getOrNull(position) ?: return
        if (context == null) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            EnablerUtil.removeBluetoothCondition(name).join()
            settingViewModel.reloadBluetoothConditions()
        }
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

        binding.diagHeader.setOnClickListener {
            diagnosticsUserToggled = true
            applyDiagnosticsExpanded(!diagnosticsExpanded)
        }
        bindShowGuideAgain()
        viewLifecycleOwner.lifecycleScope.launch {
            val anyFail = !(notiOk && listenerOk && overlayOk) || bindAccessibilityRow()
            if (!diagnosticsUserToggled) {
                applyDiagnosticsExpanded(anyFail)
            }
        }
    }

    /** 동의는 했는데 OS 에서 서비스가 꺼진 경우에만 노출한다. */
    private suspend fun bindAccessibilityRow(): Boolean {
        val ctx = context ?: return false
        val row = binding.diagRowAccessibility
        val installed = withContext(Dispatchers.IO) { AccessibilityDisclosure.isNaviInstalled(ctx) }
        val needsFix =
            installed &&
                NaviToTeslaAccessibilityService.isConsented() &&
                !NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(ctx)
        row.root.visibility = if (needsFix) View.VISIBLE else View.GONE
        if (needsFix) {
            bindDiagnosticRow(
                row,
                R.string.accessibilityService,
                R.string.guideAccessibilityRevoked,
                false,
            ) { openAccessibilitySettings() }
        }
        return needsFix
    }

    private fun showGuideAgain() =
        viewLifecycleOwner.lifecycleScope.launch {
            AccessibilityDisclosure.resetGuideVisibility()
            AnalysisUtil.makeToast(context, getString(R.string.a11yShowGuideAgainDone))
            bindShowGuideAgain()
        }

    /** 홈 안내를 숨긴 사용자에게만 복구 수단을 보여준다. */
    private fun bindShowGuideAgain() {
        binding.btnAccShowGuideAgain.visibility =
            if (AccessibilityDisclosure.isGuideHiddenSync()) View.VISIBLE else View.GONE
    }

    private fun applyDiagnosticsExpanded(expanded: Boolean) {
        if (diagnosticsAnimReady) {
            (binding.diagContent.parent as? android.view.ViewGroup)?.let { parent ->
                TransitionManager.beginDelayedTransition(
                    parent,
                    AutoTransition().apply { duration = 220 },
                )
            }
        }
        diagnosticsExpanded = expanded
        binding.diagContent.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.diagExpandIcon.rotation = if (expanded) 180f else 0f
        diagnosticsAnimReady = true
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
                "package:${requireContext().packageName}".toUri(),
            )
        runCatching { startActivity(intent) }
    }

    private fun updateConditions() =
        viewLifecycleOwner.lifecycleScope.launch {
            if (context == null || activity == null) {
                return@launch
            }
            // 앱 동작/조건/블루투스 상태는 ViewModel 이 저장소에서 로드한다(persist 하지 않음). 옵저버가 라디오/카드에 반영.
            settingViewModel.loadStates()

            launch {
                val accActive = NaviToTeslaAccessibilityService.isActive(context)
                withContext(Dispatchers.Main) { setAccRadio(accActive) }
            }
            launch {
                context?.run {
                    val enabled = PreferencesUtil.getBoolean("duplicatePoiSelection", true)
                    withContext(Dispatchers.Main) {
                        binding.radioGroupDuplicatePoiSelection.check(
                            if (enabled) binding.radioDuplicatePoiShowPopup.id else binding.radioDuplicatePoiIgnore.id,
                        )
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
                        EnablerUtil.addBluetoothCondition(selectedDevice).join()
                        settingViewModel.reloadBluetoothConditions()
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

    // 옵저버는 UI 반영만 한다. 저장은 사용자가 실제 토글했을 때(onCheckedChanged → ViewModel)만 일어난다.
    // 옵저버에서 persist 하면 초기 기본값 재방출이 저장된 설정을 덮어쓴다(조건이 계속 비활성으로 돌아가던 버그).
    private fun onChangedAppEnabled(enabled: Boolean) {
        binding.radioGroupAppEnable.check(if (enabled) binding.radioAppEnable.id else binding.radioAppDisable.id)
    }

    private fun onChangedConditionEnabled(enabled: Boolean) {
        if (conditionAnimReady) {
            (binding.cardBluetooth.parent as? android.view.ViewGroup)?.let { parent ->
                TransitionManager.beginDelayedTransition(
                    parent,
                    AutoTransition().apply { duration = 220 },
                )
            }
        }
        binding.cardBluetooth.visibility = if (enabled) View.VISIBLE else View.GONE
        conditionAnimReady = true
        binding.radioGroupConditionEnable.check(
            if (enabled) binding.radioConditionEnable.id else binding.radioConditionDisable.id,
        )
    }

    override fun onCheckedChanged(
        group: RadioGroup,
        checkedId: Int,
    ) {
        if (checkedId == R.id.radioAppEnable) {
            settingViewModel.setAppEnabledByUser(true)
        } else if (checkedId == R.id.radioAppDisable) {
            settingViewModel.setAppEnabledByUser(false)
        } else if (checkedId == R.id.radioConditionEnable) {
            settingViewModel.setConditionEnabledByUser(true)
        } else if (checkedId == R.id.radioConditionDisable) {
            settingViewModel.setConditionEnabledByUser(false)
        }
    }

    private fun onDuplicatePoiSelected(showPopup: Boolean) {
        if (showPopup && context != null && !Settings.canDrawOverlays(requireContext())) {
            showOverlayPermissionDialog()
        }
        lifecycleScope.launch { PreferencesUtil.put("duplicatePoiSelection", showPopup) }
    }

    private fun onAccEnableClicked() {
        // 동의 전에는 켜진 것으로 보이지 않아야 한다.
        setAccRadio(false)
        viewLifecycleOwner.lifecycleScope.launch {
            if (NaviToTeslaAccessibilityService.isActive(context)) {
                setAccRadio(true)
            } else {
                showAccessibilityConsentDialog()
            }
        }
    }

    /** 동의를 회수하면 OS 접근성이 켜져 있어도 수집이 멈춘다. 서비스는 안드로이드 설정에서만 끌 수 있다. */
    private fun revokeAccessibilityConsent() =
        viewLifecycleOwner.lifecycleScope.launch {
            if (!NaviToTeslaAccessibilityService.isConsented()) {
                return@launch
            }
            NaviToTeslaAccessibilityService.setConsent(false)
            val activity = activity ?: return@launch
            if (!NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(activity)) {
                return@launch
            }
            AlertDialog
                .Builder(activity)
                .setTitle(getString(R.string.guide))
                .setMessage(getString(R.string.disableAccessibility))
                .setCancelable(true)
                .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
                .setNegativeButton(getString(R.string.openAndroidSettings)) { _: DialogInterface?, _: Int -> openAccessibilitySettings() }
                .create()
                .show()
        }

    private fun setAccRadio(enable: Boolean) {
        val activity = activity ?: return
        activity.runOnUiThread {
            if (enable) {
                binding.radioAccEnable.isChecked = true
            } else {
                binding.radioAccDisable.isChecked = true
            }
        }
    }

    private fun openAccessibilitySettings() {
        val activity = activity ?: return
        AccessibilityDisclosure.openSettings(activity)
    }

    private fun showAccessibilityConsentDialog() {
        val activity = activity ?: return
        AccessibilityDisclosure.show(activity) { enabled -> setAccRadio(enabled) }
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
                            "package:${requireContext().packageName}".toUri(),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }.setNegativeButton(getString(R.string.deny)) { _: DialogInterface?, _: Int ->
                lifecycleScope.launch {
                    PreferencesUtil.put("duplicatePoiSelection", false)
                    withContext(Dispatchers.Main) {
                        binding.radioGroupDuplicatePoiSelection.check(binding.radioDuplicatePoiIgnore.id)
                    }
                }
            }.create()
            .show()
    }
}
