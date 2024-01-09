package me.zipi.navitotesla.ui.home

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.background.TokenWorker
import me.zipi.navitotesla.databinding.FragmentHomeBinding
import me.zipi.navitotesla.model.Token
import me.zipi.navitotesla.model.Vehicle
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.service.NaviToTeslaService
import me.zipi.navitotesla.util.AnalysisUtil
import me.zipi.navitotesla.util.AppUpdaterUtil
import me.zipi.navitotesla.util.PreferencesUtil
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent.setEventListener
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEventListener
import java.io.File

class HomeFragment
    : Fragment(), AdapterView.OnItemSelectedListener, View.OnClickListener, OnLongClickListener, RadioGroup.OnCheckedChangeListener {
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var binding: FragmentHomeBinding
    private lateinit var naviToTeslaService: NaviToTeslaService
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        if (this.activity != null) {
            this.requireActivity().window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        homeViewModel.vehicleListLiveData.observe(viewLifecycleOwner) { updateSpinner() }
        homeViewModel.tokenLiveData.observe(viewLifecycleOwner) { renderToken() }
        homeViewModel.appVersion.observe(viewLifecycleOwner) { renderVersion() }
        homeViewModel.isUpdateAvailable.observe(viewLifecycleOwner) { renderVersion() }
        homeViewModel.refreshToken.observe(viewLifecycleOwner) { refreshToken: String ->
            getAccessTokenAndVehicles(refreshToken)
        }
        homeViewModel.isInstalledTeslaApp.observe(viewLifecycleOwner) { isInstalled: Boolean ->
            onChangeTeslaAppInstalled(isInstalled)
        }
        homeViewModel.shareMode.observe(viewLifecycleOwner) { mode: String ->
            onChangedTeslaShareMode(
                mode,
            )
        }
        binding.txtAccessToken.movementMethod = ScrollingMovementMethod()
        binding.radioGroupShareMode.setOnCheckedChangeListener(this)
        setEventListener(
            requireActivity(),
            KeyboardVisibilityEventListener { isOpen: Boolean ->
                binding.txtVersion.visibility = if (isOpen) View.INVISIBLE else View.VISIBLE
            },
        )
        binding.btnSave.setOnClickListener(this)
        binding.btnPoiCacheClear.setOnClickListener(this)
        binding.btnPaste.setOnClickListener(this)
        binding.btnTokenClear.setOnClickListener(this)
        binding.txtVersion.setOnClickListener(this)
        binding.txtVersion.setOnLongClickListener(this)
        return root
    }

    override fun onResume() {
        super.onResume()
        accessibilityGrantedCheck()
        permissionGrantedCheck()
        CoroutineScope(Dispatchers.Default).launch {
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

    override fun onDestroyView() {
        super.onDestroyView()
        homeViewModel.clearObserve(viewLifecycleOwner)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        naviToTeslaService = NaviToTeslaService(context)
    }

    override fun onLongClick(view: View): Boolean {
        if (activity == null) {
            return false
        }
        CoroutineScope(Dispatchers.Default).launch {
            if (view.id == binding.txtVersion.id && AnalysisUtil.isWritableLog) {
                var size = (AnalysisUtil.logFileSize / 1024.0).toInt()
                var type = "KB"
                if (size > 1024) {
                    type = "MB"
                    size /= 1024
                }
                launch(Dispatchers.Main) {
                    AlertDialog.Builder(requireActivity()).setCancelable(true).setTitle(getString(R.string.viewLogFile))
                        .setMessage(getString(R.string.guideViewLogFile, size, type))
                        .setPositiveButton(getString(R.string.open)) { _: DialogInterface?, _: Int -> openLogFile() }
                        .setNegativeButton(getString(R.string.close)) { _: DialogInterface?, _: Int -> }
                        .setNeutralButton(getString(R.string.delete)) { _: DialogInterface?, _: Int ->
                            AnalysisUtil.deleteLogFile()
                        }.show()
                }
            }
        }
        return false
    }

    override fun onClick(view: View) {
        when (view.id) {
            binding.txtVersion.id -> {
                onTxtVersionClicked()
            }

            binding.btnPoiCacheClear.id -> {
                onBtnPoiCacheClearClick()
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
    private fun accessibilityGrantedCheck() {
        if (nextAction == null) {
            return
        }
        if (nextAction == "requireAccessibility") {
            if (context == null) {
                return
            }
            if (permissionAlertDialog != null && permissionAlertDialog!!.isShowing) {
                return
            }

            // accessibility check
            if (!NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(context)) {
                permissionAlertDialog = AlertDialog.Builder(requireContext()).setTitle(getString(R.string.requireAccessibility))
                    .setMessage(getString(R.string.guideRequireAccessibility))
                    .setPositiveButton(getString(R.string.confirm)) { _: DialogInterface?, _: Int -> }.setCancelable(true).show()
                nextAction = null
            }
        }
    }

    private fun permissionGrantedCheck() {
        if (context == null) {
            return
        }
        if (permissionAlertDialog != null && permissionAlertDialog!!.isShowing) {
            return
        }

        // notification listener
        val sets = NotificationManagerCompat.getEnabledListenerPackages(
            requireContext(),
        )
        if (!sets.contains(requireContext().packageName)) {
            permissionAlertDialog = AlertDialog.Builder(requireContext()).setTitle(getString(R.string.grantPermission))
                .setMessage(getString(R.string.guideGrantPermission)) // .setIcon(R.drawable.ic_launcher_background)
                .setPositiveButton(
                    getString(R.string.confirm),
                ) { _: DialogInterface?, _: Int ->
                    if (permissionAlertDialog != null) {
                        permissionAlertDialog = null
                    }
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }.setCancelable(false).show()
            return
        }
        if (context == null || activity == null) {
            return
        }
//         file write permission
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
            return
        }
        val granted =
            (requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && requireContext().checkSelfPermission(
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED)
        if (!granted) {
            permissionAlertDialog = AlertDialog.Builder(requireContext()).setTitle(this.getString(R.string.grantPermission))
                .setMessage(this.getString(R.string.guideGrantStoragePermission)).setPositiveButton(
                    this.getString(R.string.confirm),
                ) { _: DialogInterface?, _: Int ->
                    requireActivity().requestPermissions(
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        ),
                        2,
                    )
                    if (permissionAlertDialog != null) {
                        permissionAlertDialog = null
                    }
                }.setCancelable(false).show()
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
            val oldRefreshToken = binding.txtRefreshToken.text.toString()
            binding.txtRefreshToken.setText(token.refreshToken)
            binding.txtAccessToken.text = token.accessToken
            if (oldRefreshToken == token.refreshToken || homeViewModel.vehicleListLiveData.value?.size == 0) {
                homeViewModel.refreshToken.postValue(token.refreshToken)
            } else {
                Log.i(this.javaClass.name, "disable post value, refresh token is same")
            }
        }
    }

    private fun onTxtVersionClicked() {
        CoroutineScope(Dispatchers.Default).launch { AppUpdaterUtil.dialog(activity, true) }
    }

    private fun onBtnPoiCacheClearClick() {
        binding.btnPoiCacheClear.isEnabled = false
        CoroutineScope(Dispatchers.Default).launch {
            if (context == null || activity == null) {
                return@launch
            }
            try {
                naviToTeslaService.clearPoiCache()
                AppUpdaterUtil.clearDoNotShow()
                PreferencesUtil.put("lastNotifyAppVersionForAccessibility", "")
            } catch (e: Exception) {
                Log.w(this.javaClass.name, "clear poi cache error", e)
                AnalysisUtil.recordException(e)
            }
            if (activity != null) {
                withContext(Dispatchers.Main) {
                    binding.btnPoiCacheClear.isEnabled = true
                }
            }
        }
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
        CoroutineScope(Dispatchers.Default).launch {
            homeViewModel.vehicleListLiveData.postValue(mutableListOf())
            homeViewModel.refreshToken.postValue("")
            if (context != null) {
                PreferencesUtil.clear()
                homeViewModel.tokenLiveData.postValue(naviToTeslaService.getToken())
                TokenWorker.cancelBackgroundWork(requireContext())
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
        CoroutineScope(Dispatchers.IO).launch {
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
                homeViewModel.vehicleListLiveData.postValue(vehicleList)
                withContext(Dispatchers.Main) {
                    binding.btnSave.isEnabled = true
                    binding.btnSave.text = context.getString(R.string.save)
                }
            } catch (e: Exception) {
                Log.e(this.javaClass.name, "thread inside error", e)
                AnalysisUtil.recordException(e)
            }
        }
    }

    private fun updateSpinner() = lifecycleScope.launch {
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

    override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
        if (homeViewModel.vehicleListLiveData.value != null) {
            val vid: Long = homeViewModel.vehicleListLiveData.value!![i].id
            naviToTeslaService.saveVehicleId(vid)
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
            binding.txtVersion.setTextColor(Color.RED)
        }
        binding.txtVersion.text = sb.toString()
    }

    private fun openLogFile() {
        if (activity == null && !AnalysisUtil.isWritableLog) {
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                requireActivity(),
                BuildConfig.APPLICATION_ID + ".provider",
                File(AnalysisUtil.logFilePath),
            )
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "plain/text")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            AlertDialog.Builder(requireActivity()).setCancelable(true).setTitle(getString(R.string.requireLogViewApp))
                .setMessage(getString(R.string.guideRequireLogViewApp))
                .setPositiveButton(getString(R.string.install)) { _: DialogInterface?, _: Int ->
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW, Uri.parse("market://search?q=log viewer"),
                            ),
                        )
                    } catch (anfe: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/search?q=log viewer"),
                            ),
                        )
                    }
                }.setNegativeButton(getString(R.string.close)) { _: DialogInterface?, _: Int -> }.show()
        }
    }

    private val isTeslaAppInstalled: Boolean
        get() {
            if (context == null) {
                return false
            }
            return if (BuildConfig.DEBUG) {
                true
            } else try {
                requireContext().packageManager.getPackageInfo("com.teslamotors.tesla", 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                AnalysisUtil.log("package not found $e")
                false
            }
        }

    // share mode
    override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
        lifecycleScope.launch {
            val shareMode = if (binding.radioUsingTeslaApp.id == group.checkedRadioButtonId) {
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
            if (shareMode == "api") {
                binding.radioGroupShareMode.check(binding.radioUsingTeslaApi.id)
            } else {
                binding.radioGroupShareMode.check(binding.radioUsingTeslaApp.id)
                overlayPermissionGrantedCheck()
            }
        }
    }

    private fun onChangeTeslaAppInstalled(isInstalled: Boolean) {
        binding.radioUsingTeslaApp.isEnabled = isInstalled
    }

    private fun onChangedTeslaShareMode(mode: String) {
        val enableApiElement = mode != "app"
        binding.txtRefreshToken.isEnabled = enableApiElement
        binding.btnPaste.isEnabled = enableApiElement
        binding.btnSave.isEnabled = enableApiElement
        binding.vehicleSelector.isEnabled = enableApiElement
        binding.btnTokenClear.isEnabled = enableApiElement
        if (mode == "app") {
            overlayPermissionGrantedCheck()
        }
    }

    @Synchronized
    private fun overlayPermissionGrantedCheck() {
        if (activity != null) {
            requireActivity().runOnUiThread {
                if (context != null && !Settings.canDrawOverlays(
                        context,
                    ) && (permissionAlertDialog == null || !permissionAlertDialog!!.isShowing)
                ) {
                    permissionAlertDialog = AlertDialog.Builder(requireContext()).setTitle(getString(R.string.grantPermission))
                        .setMessage(getString(R.string.guideGrantOverlayPermission)).setPositiveButton(
                            getString(R.string.confirm),
                        ) { _: DialogInterface?, _: Int ->
                            if (permissionAlertDialog != null) {
                                permissionAlertDialog = null
                            }
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + requireContext().packageName),
                                ),
                            )
                        }.setNegativeButton(getString(R.string.deny)) { _: DialogInterface?, _: Int ->
                            if (permissionAlertDialog != null) {
                                permissionAlertDialog = null
                            }
                            binding.radioGroupShareMode.check(binding.radioUsingTeslaApi.id)
                        }.setCancelable(false).show()
                }
            }
        }
    }

    companion object {
        var nextAction: String? = null
    }
}
