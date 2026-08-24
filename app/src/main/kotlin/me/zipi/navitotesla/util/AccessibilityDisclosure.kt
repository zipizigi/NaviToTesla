package me.zipi.navitotesla.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.service.NaviToTeslaAccessibilityService
import me.zipi.navitotesla.service.poifinder.PoiFinderFactory

/**
 * 접근성 서비스 명시적 공개(Prominent Disclosure).
 *
 * 진입 경로와 내비 설치 여부에 관계없이 [show] 가 항상 같은 전문을 띄운다.
 * 안드로이드 접근성 설정으로 넘어가기 직전 화면은 언제나 이 다이얼로그여야 한다.
 */
object AccessibilityDisclosure {
    private const val KEY_DECLINED = "a11yDeclined"
    private const val KEY_BANNER_DISMISSED = "a11yBannerDismissed"
    private const val KEY_LAST_SHOWN_VERSION = "a11yLastShownVersionCode"

    /** 1단계 안내. 동의는 받지 않고 [show] 로만 넘긴다. */
    fun showTeaser(
        activity: FragmentActivity,
        onFinished: ((Boolean) -> Unit)? = null,
    ): AlertDialog =
        AlertDialog
            .Builder(activity)
            .setTitle(activity.getString(R.string.a11yTeaserTitle))
            .setMessage(activity.getString(R.string.a11yTeaserBody))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.a11yDetail)) { _, _ -> show(activity, onFinished) }
            .setNegativeButton(activity.getString(R.string.later)) { _, _ -> onFinished?.invoke(false) }
            .create()
            .also { it.show() }

    fun show(
        activity: FragmentActivity,
        onFinished: ((Boolean) -> Unit)? = null,
    ): AlertDialog =
        AlertDialog
            .Builder(activity)
            .setTitle(activity.getString(R.string.a11yConsentTitle))
            .setMessage(activity.getString(R.string.accessibility_description))
            .setCancelable(false)
            .setPositiveButton(activity.getString(R.string.allow)) { _, _ -> grant(activity, onFinished) }
            .setNegativeButton(activity.getString(R.string.deny)) { _, _ -> decline(activity, onFinished) }
            .create()
            .also { it.show() }

    fun openSettings(context: Context) {
        val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: ActivityNotFoundException) {
            context.startActivity(fallback)
        }
    }

    fun isNaviInstalled(context: Context): Boolean = PoiFinderFactory.isAccessibilityNaviInstalled(context.packageManager)

    /** 최초 실행 또는 업데이트 후 첫 실행에 한 번만 자동으로 띄운다. */
    suspend fun shouldAutoShow(context: Context): Boolean {
        if (NaviToTeslaAccessibilityService.isActive(context)) return false
        if (!isNaviInstalled(context)) return false
        if (PreferencesUtil.getBoolean(KEY_DECLINED, false)) return false
        return PreferencesUtil.getLong(KEY_LAST_SHOWN_VERSION, 0L) < BuildConfig.VERSION_CODE
    }

    suspend fun markAutoShown() {
        PreferencesUtil.put(KEY_LAST_SHOWN_VERSION, BuildConfig.VERSION_CODE.toLong())
    }

    /** ✕ 로 닫았거나 PD 를 거부한 상태. 첫 프레임 흔들림을 막기 위해 동기로 읽는다. */
    fun isGuideHiddenSync(): Boolean =
        PreferencesUtil.getBooleanSync(KEY_BANNER_DISMISSED, false) ||
            PreferencesUtil.getBooleanSync(KEY_DECLINED, false)

    suspend fun hideGuide() {
        PreferencesUtil.put(KEY_BANNER_DISMISSED, true)
    }

    /** 설정 탭의 '연동 안내 다시 보기'. 닫은 배너와 거부 기록을 함께 되돌린다. */
    suspend fun resetGuideVisibility() {
        PreferencesUtil.put(KEY_BANNER_DISMISSED, false)
        PreferencesUtil.put(KEY_DECLINED, false)
    }

    private fun grant(
        activity: FragmentActivity,
        onFinished: ((Boolean) -> Unit)?,
    ) = activity.lifecycleScope.launch {
        NaviToTeslaAccessibilityService.setConsent(true)
        PreferencesUtil.put(KEY_DECLINED, false)
        val enabled = NaviToTeslaAccessibilityService.isAccessibilityServiceEnabled(activity)
        if (!enabled) {
            openSettings(activity)
        }
        onFinished?.invoke(enabled)
    }

    private fun decline(
        activity: FragmentActivity,
        onFinished: ((Boolean) -> Unit)?,
    ) = activity.lifecycleScope.launch {
        PreferencesUtil.put(KEY_DECLINED, true)
        onFinished?.invoke(false)
    }
}
