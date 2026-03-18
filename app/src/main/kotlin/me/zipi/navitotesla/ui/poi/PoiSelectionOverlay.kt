package me.zipi.navitotesla.ui.poi

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.zipi.navitotesla.AppRepository
import me.zipi.navitotesla.R
import me.zipi.navitotesla.model.Poi
import me.zipi.navitotesla.service.NaviToTeslaService
import java.lang.ref.WeakReference

object PoiSelectionOverlay {
    private var windowManager: WindowManager? = null

    @SuppressLint("StaticFieldLeak")
    private var overlayView: WeakReference<View>? = null
    private var countDownTimer: CountDownTimer? = null
    private var dismissCallback: (() -> Unit)? = null
    private val hardTimeoutHandler by lazy { Handler(Looper.getMainLooper()) }
    private var hardTimeoutRunnable: Runnable? = null

    private const val PREFS_NAME = "overlay_position"

    /**
     * @return true if overlay permission is available and the overlay will be shown,
     *         false if permission is missing (caller should fallback to toast)
     */
    fun show(
        context: Context,
        candidates: List<Poi>,
        onDismissed: () -> Unit,
    ): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        hardTimeoutHandler.post {
            internalDismiss()
            dismissCallback = onDismissed

            val appContext = context.applicationContext
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val themedContext = ContextThemeWrapper(appContext, R.style.Theme_NaviToTesla)

            @SuppressLint("InflateParams")
            val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_poi_selection, null)

            val dm = appContext.resources.displayMetrics

            fun Int.dp() = (this * dm.density).toInt()
            val isPortrait = appContext.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val screenWidth = dm.widthPixels

            val cardWidth =
                if (isPortrait) {
                    minOf(300.dp(), (screenWidth * 0.72f).toInt())
                } else {
                    minOf(260.dp(), (screenWidth * 0.36f).toInt())
                }
            val recyclerHeight = if (isPortrait) 282.dp() else 182.dp()

            val overlayType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
            val params =
                WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                )
            params.gravity = Gravity.BOTTOM or Gravity.START

            // Load saved position, fall back to defaults
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val orientationKey = if (isPortrait) "portrait" else "landscape"
            params.x = prefs.getInt("x_$orientationKey", 16.dp())
            params.y = prefs.getInt("y_$orientationKey", if (isPortrait) 130.dp() else 80.dp())

            // Slight transparency so underlying content is still visible
            view.alpha = 0.9f

            val titleText = view.findViewById<TextView>(R.id.textOverlayTitle)
            val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerPoiSelection)
            val closeButton = view.findViewById<Button>(R.id.btnCancelSelection)
            val dragHandle = view.findViewById<View>(R.id.dragHandle)

            val poiName = candidates.firstOrNull()?.poiName ?: ""
            titleText.text = appContext.getString(R.string.overlaySelectAddressTitle, poiName)

            recyclerView.layoutParams = recyclerView.layoutParams.apply { height = recyclerHeight }

            val adapter =
                PoiSelectionAdapter { selectedPoi ->
                    // User made a selection — clear callback so dismiss() won't fire toast
                    dismissCallback = null
                    dismiss()
                    if (AppRepository.isInitialized()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            AppRepository.getInstance().savePoi(selectedPoi, false)
                            NaviToTeslaService(appContext).share(selectedPoi)
                        }
                    }
                }
            adapter.setItems(candidates)
            recyclerView.adapter = adapter
            recyclerView.layoutManager = LinearLayoutManager(appContext)
            recyclerView.addItemDecoration(DividerItemDecoration(appContext, DividerItemDecoration.VERTICAL))

            recyclerView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int,
                    ) {
                        if (dy != 0 && countDownTimer != null) {
                            countDownTimer?.cancel()
                            countDownTimer = null
                            closeButton.text = appContext.getString(R.string.overlayClose)
                        }
                    }
                },
            )

            closeButton.setOnClickListener { dismiss() }

            // Drag-to-reposition via header handle
            var dragStartRawX = 0f
            var dragStartRawY = 0f
            var dragStartParamsX = 0
            var dragStartParamsY = 0

            @SuppressLint("ClickableViewAccessibility")
            val dragTouchListener =
                View.OnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            dragStartRawX = event.rawX
                            dragStartRawY = event.rawY
                            dragStartParamsX = params.x
                            dragStartParamsY = params.y
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - dragStartRawX).toInt()
                            val dy = (event.rawY - dragStartRawY).toInt()
                            // GRAVITY_BOTTOM: y increases upward, so invert dy
                            params.x = (dragStartParamsX + dx).coerceAtLeast(0)
                            params.y = (dragStartParamsY - dy).coerceAtLeast(0)
                            wm.updateViewLayout(view, params)
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            prefs
                                .edit()
                                .putInt("x_$orientationKey", params.x)
                                .putInt("y_$orientationKey", params.y)
                                .apply()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }
            dragHandle.setOnTouchListener(dragTouchListener)

            // Hard 60s timeout — always fires regardless of scroll
            hardTimeoutRunnable?.let { hardTimeoutHandler.removeCallbacks(it) }
            hardTimeoutRunnable =
                Runnable { dismiss() }.also {
                    hardTimeoutHandler.postDelayed(it, 60_000L)
                }

            countDownTimer =
                object : CountDownTimer(30_000L, 1_000L) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000).toInt()
                        closeButton.text = appContext.getString(R.string.overlayCloseWithTimer, seconds)
                    }

                    override fun onFinish() {
                        dismiss()
                    }
                }.start()

            wm.addView(view, params)
            windowManager = wm
            overlayView = WeakReference(view)
        }
        return true
    }

    /** Dismiss and fire onDismissed callback (user closed without selecting). */
    fun dismiss() {
        val callback = dismissCallback
        dismissCallback = null
        internalDismiss()
        callback?.invoke()
    }

    /** Dismiss silently without firing any callback. */
    private fun internalDismiss() {
        countDownTimer?.cancel()
        countDownTimer = null
        hardTimeoutRunnable?.let { hardTimeoutHandler.removeCallbacks(it) }
        hardTimeoutRunnable = null
        try {
            overlayView?.get()?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
            // View may already be detached
        }
        overlayView = null
        windowManager = null
    }
}
