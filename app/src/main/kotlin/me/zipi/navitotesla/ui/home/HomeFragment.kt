package me.zipi.navitotesla.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButtonToggleGroup
import com.gun0912.tedpermission.coroutine.TedPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.background.TokenWorker
import me.zipi.navitotesla.databinding.FragmentHomeBinding
import me.zipi.navitotesla.model.SendMode
import me.zipi.navitotesla.model.Token
import me.zipi.navitotesla.model.Vehicle
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import me.zipi.navitotesla.util.PreferencesUtil
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent.setEventListener
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener

class HomeFragment :
    Fragment(),
    AdapterView.OnItemSelectedListener,
    View.OnClickListener,
    MaterialButtonToggleGroup.OnButtonCheckedListener {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var binding: FragmentHomeBinding
    private lateinit var naviToTeslaService: NaviToTeslaService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        if (this.activity != null) {
            this.requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        homeViewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 첫 프레임 깜빡임 방지: shareMode 를 동기로 읽어 cardToken/cardVehicle visibility 즉시 적용.
        // LiveData observer 가 이후 같은 값으로 재호출해도 멱등.
        val initialShareMode = PreferencesUtil.getStringSync("shareMode", "app") ?: "app"
        applyShareModeVisibility(useApi = initialShareMode == "api")

        homeViewModel.vehicleListLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { updateSpinner() }
        homeViewModel.tokenLiveData.distinctUntilChanged().observe(viewLifecycleOwner) { renderToken() }
        homeViewModel.appVersion.distinctUntilChanged().observe(viewLifecycleOwner) { renderVersion() }
        homeViewModel.isUpdateAvailable.distinctUntilChanged().observe(viewLifecycleOwner) { renderVersion() }
        homeViewModel.refreshToken.distinctUntilChanged().observe(viewLifecycleOwner) { refreshToken: String ->
            getAccessTokenAndVehicles(refreshToken)
        }
        homeViewModel.isInstalledTeslaApp.distinctUntilChanged().observe(viewLifecycleOwner) { isInstalled: Boolean ->
            onChangeTeslaAppInstalled(isInstalled)
        }
        homeViewModel.shareMode.distinctUntilChanged().observe(viewLifecycleOwner) { mode: String ->
            onChangedTeslaShareMode(
                mode,
            )
        }
        binding.txtAccessToken.movementMethod = ScrollingMovementMethod()
        binding.radioGroupShareMode.addOnButtonCheckedListener(this)
        setEventListener(
            requireActivity(),
            KeyboardVisibilityEventListener { isOpen: Boolean ->
                binding.txtVersion.visibility = if (isOpen) View.INVISIBLE else View.VISIBLE
            },
        )
        binding.btnSave.setOnClickListener(this)
        binding.btnPaste.setOnClickListener(this)
        binding.btnTokenClear.setOnClickListener(this)
        binding.txtVersion.setOnClickListener(this)
        setupSendModeRadios()
        loadSendModeRadios()

        lifecycleScope.launch(Dispatchers.Default) {
            permissionGrantedCheck()
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            accessibilityGrantedCheck()
            permissionNotificationListenerGrantedCheck()
        }
        lifecycleScope.launch(Dispatchers.Default) {
            launch { updateVersion() }
            launch { updateToken() }
            launch { updateLatestVersion() }
            launch { updateShareMode() }
            launch { homeViewModel.isInstalledTeslaApp.postValue(isTeslaAppInstalled) }
            if (permissionAlertDialog == null || !permissionAlertDialog!!.isShowing) {
                launch { AppUpdaterUtil.dialog(activity, false) }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        naviToTeslaService = NaviToTeslaService(context)
    }

    override fun onClick(view: View) {
        when (view.id) {
            binding.txtVersion.id -> {
                onTxtVersionClicked()
            }

            binding.btnPaste.id -> {
                onBtnPasteClick()
            }

            binding.btnTokenClear.id -> {
                onBtnTokenClearClick()
            }

            binding.btnSave.id -> {
                onBtnSaveClick(binding.txtRefreshToken.text.toString().trim())
            }
        }
    }

    private var permissionAlertDialog: AlertDialog? = null

    private suspend fun accessibilityGrantedCheck() {
        if (nextAction == null) {
            return
        }
        if (nextAction != "requireAccessibility") {
            return
        }
        if (context == null) {
            return
        }
        if (permissionAlertDialog != null && permissionAlertDialog!!.isShowing) {
            return
        }

        val enabled =
            withContext(Dispatchers.IO) {
                NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(context)
            }
        if (enabled) {
            return
        }
        permissionAlertDialog =
            AlertDialog
                .Builder(requireContext())
                .setTitle(getString(R.string.requireAccessibility))
                .setMessage(getString(R.string.guideRequireAccessibility))
                .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }
                .setCancelable(true)
                .show()
        nextAction = null
    }

    private suspend fun permissionGrantedCheck() {
        if (context == null || activity == null) {
            return
        }
        if (permissionAlertDialog != null && permissionAlertDialog!!.isShowing) {
            return
        }

        //         file write permission
        // 권한 이미 grant 되어 있으면 TedPermission 호출 자체 skip — TedPermissionActivity launch 안 됨 → 탭 전환 즉시.
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && !PreferencesUtil.getBoolean("denyFilePermission", false))) {
            val granted =
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                withContext(Dispatchers.Main) {
                    TedPermission
                        .create()
                        .setRationaleTitle(R.string.grantPermission)
                        .setRationaleMessage(R.string.guideGrantStoragePermission)
                        .setDeniedMessage(R.string.guidePermissionDeny)
                        .setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                        .check()
                        .run {
                            if (!isGranted) {
                                PreferencesUtil.put("denyFilePermission", true)
                            }
                        }
                }
            }
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PreferencesUtil.getBoolean("denyNotificationPermission", false))) {
            val granted =
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                withContext(Dispatchers.Main) {
                    TedPermission
                        .create()
                        .setRationaleTitle(R.string.grantPermission)
                        .setRationaleMessage(R.string.guideGrantNotificationPermission)
                        .setDeniedMessage(R.string.guidePermissionDeny)
                        .setPermissions(Manifest.permission.POST_NOTIFICATIONS)
                        .check()
                        .run {
                            if (!isGranted) {
                                PreferencesUtil.put("denyNotificationPermission", true)
                            }
                        }
                }
            }
        }
    }

    private suspend fun permissionNotificationListenerGrantedCheck() {
        if (context == null) {
            return
        }
        if (permissionAlertDialog != null && permissionAlertDialog!!.isShowing) {
            return
        }

        // notification listener
        val sets =
            withContext(Dispatchers.IO) {
                NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            }
        if (!sets.contains(requireContext().packageName)) {
            permissionAlertDialog =
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(getString(R.string.grantPermission))
                    .setMessage(getString(R.string.guideGrantPermission)) // .setIcon(R.drawable.ic_launcher_background)
                    .setPositiveButton(
                        getString(R.string.confirm),
                    ) { _: DialogInterface?, _: Int ->
                        if (permissionAlertDialog != null) {
                            permissionAlertDialog = null
                        }
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }.setCancelable(false)
                    .show()
            return
        }
    }

    private suspend fun updateToken() {
        homeViewModel.tokenLiveData.postValue(naviToTeslaService.getToken())
    }

    private fun renderToken() {
        val token: Token? = homeViewModel.tokenLiveData.value
        if (token == null) {
            binding.txtRefreshToken.setText("")
            binding.txtAccessToken.text = ""
        } else {
            binding.txtRefreshToken.setText(token.refreshToken)
            binding.txtAccessToken.text = token.accessToken
            if (homeViewModel.refreshToken.value != token.refreshToken || homeViewModel.vehicleListLiveData.value.isNullOrEmpty()) {
                homeViewModel.refreshToken.postValue(token.refreshToken)
            } else {
                AnalysisUtil.log("skip refresh token fetch, token unchanged and vehicles loaded")
            }
        }
        refreshTokenButtonEnabled()
    }

    private fun onTxtVersionClicked() {
        lifecycleScope.launch(Dispatchers.Default) { AppUpdaterUtil.dialog(activity, true) }
    }

    private fun onBtnPasteClick() {
        if (activity != null) {
            val clipboard = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            if (clipboard.primaryClip?.getItemAt(0)?.text != null) {
                val pasteData = clipboard.primaryClip!!.getItemAt(0).text.toString().trim()
                if (pasteData.matches("(^[\\w-]*\\.[\\w-]*\\.[\\w-]*$)".toRegex())) {
                    binding.txtRefreshToken.setText(pasteData)
                }
            }
        }
    }

    private fun onBtnTokenClearClick() {
        if (activity == null) return
        AlertDialog
            .Builder(requireActivity())
            .setTitle(getString(R.string.dialogClearTokenTitle))
            .setMessage(getString(R.string.dialogClearTokenMessage))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.delete)) { _: DialogInterface?, _: Int ->
                performTokenClear()
            }.setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> }
            .show()
    }

    private fun performTokenClear() {
        lifecycleScope.launch(Dispatchers.Default) {
            homeViewModel.vehicleListLiveData.postValue(mutableListOf())
            homeViewModel.refreshToken.postValue("")
            if (context != null) {
                // 다른 사용자 설정 (shareMode, defaultSendMode 등) 보존을 위해 토큰/차량 키만 삭제.
                PreferencesUtil.remove("refreshToken")
                PreferencesUtil.remove("accessToken")
                PreferencesUtil.remove("tokenUpdated")
                PreferencesUtil.remove("vehicleId")
                homeViewModel.tokenLiveData.postValue(naviToTeslaService.getToken())
                TokenWorker.cancelBackgroundWork(requireContext())
                withContext(Dispatchers.Main) {
                    refreshTokenButtonEnabled()
                }
            }
        }
    }

    private fun onBtnSaveClick(refreshToken: String?) {
        if (refreshToken.isNullOrEmpty()) {
            return
        }
        if (homeViewModel.refreshToken.value == refreshToken) {
            return
        }
        homeViewModel.refreshToken.postValue(refreshToken)
    }

    private fun getAccessTokenAndVehicles(refreshToken: String?) {
        if (refreshToken.isNullOrEmpty()) {
            return
        }
        if (!binding.btnSave.isEnabled) {
            return
        }
        // Cooldown gate: refreshToken observer → renderToken → may post refreshToken back,
        // which would re-enter this method. tokenChanged + cooldown breaks that loop.
        // Do not remove without re-introducing equivalent loop guard.
        val tokenChanged = homeViewModel.lastFetchedToken != refreshToken
        val noVehicles = homeViewModel.vehicleListLiveData.value.isNullOrEmpty()
        val withinCooldown = (System.currentTimeMillis() - homeViewModel.lastTokenFetchTime) < 10 * 60 * 1000L
        if (!tokenChanged && !noVehicles && withinCooldown) {
            return
        }
        binding.btnSave.isEnabled = false
        binding.btnSave.text = getString(R.string.checking)
        if (activity != null) {
            val focusView = requireActivity().currentFocus
            if (focusView != null) {
                val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(focusView.windowToken, 0)
            }
        }
        val context: Activity = requireActivity()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = naviToTeslaService.refreshToken(refreshToken)
                if (homeViewModel.tokenLiveData.value != token) {
                    homeViewModel.tokenLiveData.postValue(token)
                    TokenWorker.startBackgroundWork(context)
                }
                val vehicleList = naviToTeslaService.getVehicles(token)
                if (vehicleList.isNotEmpty()) {
                    if (naviToTeslaService.loadVehicleId() == 0L) {
                        naviToTeslaService.saveVehicleId(vehicleList[0].id)
                    }
                }
                homeViewModel.lastFetchedToken = refreshToken
                homeViewModel.lastTokenFetchTime = System.currentTimeMillis()
                homeViewModel.vehicleListLiveData.postValue(vehicleList)
                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = context.getString(R.string.save)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                }
                AnalysisUtil.error("thread inside error", e)
                AnalysisUtil.recordException(e)
            }
        }
    }

    private fun updateSpinner() =
        lifecycleScope.launch {
            val id = naviToTeslaService.loadVehicleId()
            val spinnerArray: MutableList<String> = ArrayList()
            if (homeViewModel.vehicleListLiveData.value == null) {
                return@launch
            }
            var spinnerIndex = 0
            for (i in 0 until homeViewModel.vehicleListLiveData.value!!.size) {
                val v: Vehicle = homeViewModel.vehicleListLiveData.value!![i]
                spinnerArray.add(v.displayName)
                if (v.id == id) {
                    spinnerIndex = i
                }
            }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, spinnerArray)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            val spinner = binding.vehicleSelector
            spinner.adapter = adapter
            spinner.setSelection(spinnerIndex)
            spinner.onItemSelectedListener = this@HomeFragment
        }

    override fun onItemSelected(
        adapterView: AdapterView<*>?,
        view: View?,
        i: Int,
        l: Long,
    ) {
        if (homeViewModel.vehicleListLiveData.value != null) {
            val vid: Long = homeViewModel.vehicleListLiveData.value!![i].id
            lifecycleScope.launch(Dispatchers.IO) { naviToTeslaService.saveVehicleId(vid) }
        }
    }

    override fun onNothingSelected(adapterView: AdapterView<*>?) {}

    private fun updateVersion() {
        homeViewModel.appVersion.postValue(AppUpdaterUtil.getCurrentVersion(this.context))
    }

    private suspend fun updateLatestVersion() {
        homeViewModel.isUpdateAvailable.postValue(AppUpdaterUtil.isUpdateAvailable(context))
    }

    private fun renderVersion() {
        val sb = StringBuilder()
        sb.append(homeViewModel.appVersion.value)
        if (homeViewModel.isUpdateAvailable.value == true) {
            sb.append("\n").append("(").append(getString(R.string.updateAvailable)).append(")")
            binding.txtVersion.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(
                    binding.txtVersion,
                    androidx.appcompat.R.attr.colorError,
                ),
            )
        }
        binding.txtVersion.text = sb.toString()
    }

    private val isTeslaAppInstalled: Boolean
        get() {
            if (context == null) {
                return false
            }
            return if (BuildConfig.DEBUG) {
                true
            } else {
                try {
                    requireContext().packageManager.getPackageInfo("com.teslamotors.tesla", 0)
                    true
                } catch (e: PackageManager.NameNotFoundException) {
                    AnalysisUtil.log("package not found $e")
                    false
                }
            }
        }

    // share mode
    override fun onButtonChecked(
        group: MaterialButtonToggleGroup,
        checkedId: Int,
        isChecked: Boolean,
    ) {
        if (!isChecked) {
            return
        }
        lifecycleScope.launch {
            val shareMode =
                if (checkedId == binding.radioUsingTeslaApp.id) {
                    "app"
                } else {
                    "api"
                }
            if (homeViewModel.shareMode.value == shareMode) {
                return@launch
            }
            homeViewModel.shareMode.postValue(shareMode)
            if (context != null) {
                PreferencesUtil.put("shareMode", shareMode)
            }
        }
    }

    private suspend fun updateShareMode() {
        if (activity == null) {
            return
        }
        val isAppInstalled = isTeslaAppInstalled
        var shareMode = PreferencesUtil.getString("shareMode", null)
        if (shareMode == null) {
            if (isAppInstalled) {
                PreferencesUtil.put("shareMode", "app")
                shareMode = "app"
            } else {
                PreferencesUtil.put("shareMode", "api")
                shareMode = "api"
            }
        }
        homeViewModel.shareMode.postValue(shareMode)
        if (activity == null) {
            return
        }

        withContext(Dispatchers.Main) {
            val targetId =
                if (shareMode == "api") {
                    binding.radioUsingTeslaApi.id
                } else {
                    binding.radioUsingTeslaApp.id
                }
            if (binding.radioGroupShareMode.checkedButtonId != targetId) {
                binding.radioGroupShareMode.check(targetId)
            }
            if (shareMode != "api") {
                overlayPermissionGrantedCheck()
            }
        }
    }

    private fun onChangeTeslaAppInstalled(isInstalled: Boolean) {
        binding.radioUsingTeslaApp.isEnabled = isInstalled
    }

    private fun onChangedTeslaShareMode(mode: String) {
        val useApi = mode != "app"
        applyShareModeVisibility(useApi)
        binding.txtRefreshToken.isEnabled = useApi
        binding.btnPaste.isEnabled = useApi
        binding.btnSave.isEnabled = useApi
        binding.vehicleSelector.isEnabled = useApi
        refreshTokenButtonEnabled()
        if (mode == "app") {
            overlayPermissionGrantedCheck()
        }
    }

    private var shareModeVisibilityApplied = false

    private fun applyShareModeVisibility(useApi: Boolean) {
        val visibility = if (useApi) View.VISIBLE else View.GONE
        // cold start (첫 호출) 에는 animation 없이 즉시 적용 — XML 기본 GONE 과 일치하면 noop, Api 모드면 즉시 VISIBLE.
        // 이후 사용자 토글 시 fade + slide bounds (AutoTransition) 으로 자연스럽게 전환.
        if (shareModeVisibilityApplied) {
            val parent = binding.cardToken.parent as? ViewGroup
            if (parent != null) {
                TransitionManager.beginDelayedTransition(
                    parent,
                    Slide(android.view.Gravity.TOP).apply { duration = 220 },
                )
            }
        }
        binding.cardToken.visibility = visibility
        binding.cardVehicle.visibility = visibility
        shareModeVisibilityApplied = true
    }

    private fun refreshTokenButtonEnabled() {
        if (!isAdded || view == null) return
        val useApi = binding.radioGroupShareMode.checkedButtonId == binding.radioUsingTeslaApi.id
        val hasToken = PreferencesUtil.loadTokenSync() != null
        binding.btnTokenClear.isEnabled = useApi && hasToken
    }

    private fun overlayPermissionGrantedCheck() {
        if (context != null &&
            !Settings.canDrawOverlays(context) &&
            (permissionAlertDialog == null || !permissionAlertDialog!!.isShowing)
        ) {
            permissionAlertDialog =
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(getString(R.string.grantPermission))
                    .setMessage(getString(R.string.guideGrantOverlayPermission))
                    .setPositiveButton(
                        getString(R.string.confirm),
                    ) { _: DialogInterface?, _: Int ->
                        if (permissionAlertDialog != null) {
                            permissionAlertDialog = null
                        }
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                ("package:" + requireContext().packageName).toUri(),
                            ),
                        )
                    }.setNegativeButton(getString(R.string.deny)) { _: DialogInterface?, _: Int ->
                        if (permissionAlertDialog != null) {
                            permissionAlertDialog = null
                        }
                        binding.radioGroupShareMode.check(binding.radioUsingTeslaApi.id)
                    }.setCancelable(false)
                    .show()
        }
    }

    private fun setupSendModeRadios() {
        binding.radioGroupDefaultSendMode.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            when (checkedId) {
                R.id.radioDefaultSendModeRoad -> persistDefaultSendMode(SendMode.ROAD)
                R.id.radioDefaultSendModeJibun -> persistDefaultSendMode(SendMode.JIBUN)
                R.id.radioDefaultSendModeName -> persistDefaultSendMode(SendMode.NAME)
            }
        }
        binding.radioGroupFallbackSendMode.setOnCheckedChangeListener { _: RadioGroup?, checkedId: Int ->
            when (checkedId) {
                R.id.radioFallbackSendModeRoad -> persistFallbackSendMode(SendMode.ROAD)
                R.id.radioFallbackSendModeJibun -> persistFallbackSendMode(SendMode.JIBUN)
                R.id.radioFallbackSendModeName -> persistFallbackSendMode(SendMode.NAME)
            }
        }
    }

    private fun radioIdForDefaultMode(mode: SendMode): Int =
        when (mode) {
            SendMode.JIBUN -> binding.radioDefaultSendModeJibun.id
            SendMode.NAME -> binding.radioDefaultSendModeName.id
            else -> binding.radioDefaultSendModeRoad.id
        }

    private fun radioIdForFallbackMode(mode: SendMode): Int =
        when (mode) {
            SendMode.JIBUN -> binding.radioFallbackSendModeJibun.id
            SendMode.NAME -> binding.radioFallbackSendModeName.id
            else -> binding.radioFallbackSendModeRoad.id
        }

    private fun persistDefaultSendMode(mode: SendMode) {
        lifecycleScope.launch { PreferencesUtil.setDefaultSendMode(mode) }
    }

    private fun persistFallbackSendMode(mode: SendMode) {
        lifecycleScope.launch { PreferencesUtil.setFallbackSendMode(mode) }
    }

    private fun loadSendModeRadios() {
        viewLifecycleOwner.lifecycleScope.launch {
            val default = PreferencesUtil.getDefaultSendMode()
            val fallback = PreferencesUtil.getFallbackSendMode()
            withContext(Dispatchers.Main) {
                if (!isAdded || view == null) return@withContext
                binding.radioGroupDefaultSendMode.setOnCheckedChangeListener(null)
                binding.radioGroupDefaultSendMode.check(radioIdForDefaultMode(default))
                binding.radioGroupFallbackSendMode.setOnCheckedChangeListener(null)
                binding.radioGroupFallbackSendMode.check(radioIdForFallbackMode(fallback))
                setupSendModeRadios()
            }
        }
    }

    companion object {
        var nextAction: String? = null
    }
}
