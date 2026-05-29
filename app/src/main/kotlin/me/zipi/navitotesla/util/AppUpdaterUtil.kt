package me.zipi.navitotesla.util

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.dcastalia.localappupdate.DownloadApk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zipi.navitotesla.BuildConfig
import me.zipi.navitotesla.R
import me.zipi.navitotesla.api.GithubApi
import me.zipi.navitotesla.model.Github.Release
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object AppUpdaterUtil {
    private val githubApi =
        Retrofit
            .Builder()
            .baseUrl("https://api.github.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient
                    .Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(HttpRetryInterceptor(10))
                    .build(),
            ).build()
            .create(GithubApi::class.java)
    private var dialogLastCheck = 0L
    private var notificationLastCheck = 0L

    fun clearDoNotShow() {
        CoroutineScope(Dispatchers.IO).launch {
            PreferencesUtil.remove("updateDoNotShow")
        }
    }

    private fun doNotShow() {
        CoroutineScope(Dispatchers.IO).launch {
            val until = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L
            PreferencesUtil.put("updateDoNotShow", until)
        }
    }

    private suspend fun isDoNotShow(): Boolean {
        val until = PreferencesUtil.getLong("updateDoNotShow", 0L)
        return System.currentTimeMillis() - until < 0
    }

    suspend fun dialog(
        activity: Activity?,
        isForce: Boolean,
    ) = withContext(Dispatchers.Main) {
        try {
            if (activity == null) {
                return@withContext
            }
            if (BuildConfig.DEBUG) {
                return@withContext
            }

            if (!isForce && (abs(System.currentTimeMillis() - dialogLastCheck) < 5 * 60 * 1000 || isDoNotShow())) {
                return@withContext
            }
            dialogLastCheck = System.currentTimeMillis()
            if (isUpdateAvailable(activity)) {
                if (!permissionCheck(activity)) {
                    return@withContext
                }
                try {
                    var release: Release? = null
                    val response =
                        githubApi.getReleases(
                            RemoteConfigUtil.getString("repoOwner"),
                            RemoteConfigUtil.getString("repoName"),
                        )

                    if (response.code() == 403) {
                        AnalysisUtil.warn("github api rate limit exceed")
                    } else if (!response.isSuccessful || response.body() == null) {
                        AnalysisUtil.warn("github api call fail. Http code: " + response.code())
                        response.errorBody()?.string()?.let {
                            AnalysisUtil.warn(it)
                            AnalysisUtil.recordException(RuntimeException())
                        }
                    } else {
                        release = response.body()?.firstOrNull { it.isPreRelease != true }
                    }
                    val apkUrl = getLatestApkUrl(release)
                    val releaseDescription =
                        release?.let { "${it.tagName}\n${extractDescription(it.body)}" }.orEmpty()

                    AlertDialog
                        .Builder(
                            activity,
                        ).setCancelable(true)
                        .setTitle(activity.getString(R.string.existsUpdate))
                        .setMessage(releaseDescription)
                        .setPositiveButton(activity.getString(R.string.update)) { _: DialogInterface?, _: Int ->
                            startUpdate(
                                activity,
                                apkUrl,
                            )
                        }.setNeutralButton(activity.getString(R.string.ignoreUpdate)) { _: DialogInterface?, _: Int ->
                            AlertDialog
                                .Builder(
                                    activity,
                                ).setTitle(activity.getString(R.string.guide))
                                .setMessage(activity.getString(R.string.guideIgnoreUpdate))
                                .setCancelable(false)
                                .setPositiveButton(activity.getString(R.string.confirm)) { _: DialogInterface?, _: Int ->
                                    doNotShow()
                                }.show()
                        }.setNegativeButton(activity.getString(R.string.close)) { _: DialogInterface?, _: Int -> }
                        .show()

                    permissionCheck(activity)
                    ResponseCloser.closeAll(response)
                } catch (e: Exception) {
                    AnalysisUtil.warn("error update dialog show", e)
                    AnalysisUtil.recordException(e)
                }
            }
        } catch (e: NullPointerException) {
            AnalysisUtil.warn("activity is null", e)
        }
    }

    @Suppress("KotlinConstantConditions")
    private fun startUpdate(
        context: Context,
        apkUrl: String,
    ) {
        try {
            AnalysisUtil.log("Start update app")
            val appPackageName =
                if (BuildConfig.DEBUG) {
                    context.packageName.replace(
                        ".debug",
                        "",
                    )
                } else {
                    context.packageName
                }
            if (isPlayStoreInstalled(context) && BuildConfig.BUILD_MODE == "playstore") {
                try {
                    AnalysisUtil.log("Start update app - open play store")
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=$appPackageName".toUri(),
                        ),
                    )
                    clearDoNotShow()
                    return
                } catch (e: ActivityNotFoundException) {
                    AnalysisUtil.warn("play store is installed, but launch error")
                    AnalysisUtil.recordException(e)
                }
            }
            if (BuildConfig.BUILD_MODE != "playstore") {
                if (apkUrl.contains(".apk")) {
                    DownloadApk(context).startDownloadingApk(
                        apkUrl,
                        apkUrl
                            .split("/".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()[
                            apkUrl
                                .split("/".toRegex())
                                .dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                                .size - 1,
                        ].replace(".apk", ""),
                    )
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, apkUrl.toUri()))
                }
            } else {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$appPackageName".toUri(),
                    )
                context.startActivity(intent)
            }
            clearDoNotShow()
        } catch (e: Exception) {
            AnalysisUtil.warn("fail update", e)
            AnalysisUtil.recordException(e)
        }
    }

    private suspend fun getLatestVersion(): String =
        withContext(Dispatchers.IO) {
            try {
                val latestUrl =
                    String.format(
                        "https://github.com/%s/%s/releases/latest",
                        RemoteConfigUtil.getString("repoOwner"),
                        RemoteConfigUtil.getString("repoName"),
                    )
                val con = URL(latestUrl).openConnection() as HttpURLConnection
                con.instanceFollowRedirects = false
                con.connect()
                if (con.responseCode == 302 || con.responseCode == 304) {
                    val location = con.getHeaderField("Location")
                    return@withContext location
                        .split("/".toRegex())
                        .dropLastWhile { it.isEmpty() }
                        .toTypedArray()[
                        location
                            .split("/".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                            .size - 1,
                    ]
                }
            } catch (e: Exception) {
                AnalysisUtil.warn("getLatestVersion fail", e)
                AnalysisUtil.recordException(e)
            }
            return@withContext "1.0"
        }

    fun getLatestApkUrl(release: Release?): String {
        val defaultApkUrl =
            String.format(
                "https://github.com/%s/%s/releases/latest",
                RemoteConfigUtil.getString("repoOwner"),
                RemoteConfigUtil.getString("repoName"),
            )
        return release
            ?.assets
            ?.firstOrNull { it.contentType == "application/vnd.android.package-archive" }
            ?.downloadUrl
            ?: defaultApkUrl
    }

    fun getCurrentVersion(context: Context?): String {
        var version = "1.0"
        if (context == null || context.packageManager == null) {
            return version
        }
        try {
            version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            AnalysisUtil.recordException(e)
        }
        return version
    }

    suspend fun isUpdateAvailable(context: Context?): Boolean {
        val latestVersion = getLatestVersion()
        if (latestVersion == "1.0") return false
        val currentVersion = getCurrentVersion(context)
        return compareVersion(currentVersion, latestVersion) < 0
    }

    internal fun compareVersion(
        a: String,
        b: String,
    ): Int {
        val ap = parseVersion(a)
        val bp = parseVersion(b)
        for (i in 0 until maxOf(ap.size, bp.size)) {
            val l = ap.getOrNull(i) ?: 0
            val r = bp.getOrNull(i) ?: 0
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun parseVersion(v: String): List<Int> = v.split(Regex("[.-]")).mapNotNull { it.toIntOrNull() }

    private fun permissionCheck(activity: Activity): Boolean {
        // playstore 빌드는 자체 APK 다운로드 경로가 없어 외부 저장소 권한 자체가 불필요.
        if (BuildConfig.BUILD_MODE == "playstore") {
            return true
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) {
            return true
        }
        val granted =
            (
                activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            )
        if (!granted) {
            activity.runOnUiThread {
                AlertDialog
                    .Builder(activity)
                    .setTitle(activity.getString(R.string.grantPermission))
                    .setMessage(activity.getString(R.string.guideGrantStoragePermission))
                    .setPositiveButton(activity.getString(R.string.confirm)) { _: DialogInterface?, _: Int ->
                        activity.requestPermissions(
                            arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                            ),
                            2,
                        )
                    }.setCancelable(false)
                    .show()
            }
        }
        return true
    }

    @Suppress("KotlinConstantConditions")
    suspend fun notification(context: Context) {
        if (BuildConfig.BUILD_MODE == "playstore" || BuildConfig.DEBUG) {
            return
        }
        if (abs(System.currentTimeMillis() - notificationLastCheck) < 5 * 60 * 1000 || isDoNotShow()) {
            return
        }
        notificationLastCheck = System.currentTimeMillis()
        if (isUpdateAvailable(context)) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mChannel =
                    NotificationChannel(
                        "update_notification_channel",
                        "Update notification",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                notificationManager.createNotificationChannel(mChannel)
            }
            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    0,
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, "update_notification_channel")
                    .setContentIntent(contentIntent)
                    .setContentTitle(context.getString(R.string.updateAvailable))
                    .setContentText(context.getString(R.string.guideUpdateAvailable))
                    .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(0, notification)
        }
    }

    private fun isPlayStoreInstalled(context: Context): Boolean =
        try {
            context.packageManager.getPackageInfo("com.android.vending", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    private val HTML_COMMENT_REGEX = Regex("(?s)<!--.*?-->")
    private const val AUTO_NOTES_MARKER = "## What's Changed"

    /**
     * GitHub 릴리즈 본문에서 사용자 description 만 추려낸다.
     * - HTML 주석 블록(`<!-- ... -->`) 제거 (신규 워크플로가 자동 노트를 여기 감싸 둠)
     * - 그래도 남아있는 자동 생성 마커(`## What's Changed` 이후) 잘라냄 (구버전 릴리즈 호환)
     */
    internal fun extractDescription(body: String?): String {
        if (body == null) return ""
        val withoutComments = body.replace(HTML_COMMENT_REGEX, "")
        return withoutComments.substringBefore(AUTO_NOTES_MARKER).trim()
    }
}
