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
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
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
import java.lang.Float.parseFloat
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object AppUpdaterUtil {
    private val githubApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(
                OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(HttpRetryInterceptor(10))
                    .build(),
            )
            .build().create(GithubApi::class.java)
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

            if (!isForce && (abs(System.currentTimeMillis() - dialogLastCheck) < 5 * 60 * 1000 || isDoNotShow())) {
                return@withContext
            }
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
                        AnalysisUtil.log("github api rate limit exceed")
                    } else if (!response.isSuccessful || response.body() == null) {
                        AnalysisUtil.log("github api call fail. Http code: " + response.code())
                        if (response.errorBody() != null) {
                            AnalysisUtil.log(response.errorBody()!!.string())
                            AnalysisUtil.recordException(RuntimeException())
                        }
                    } else {
                        for (r in response.body()!!) {
                            if (r.isPreRelease == false) {
                                release = r
                                break
                            }
                        }
                    }
                    val apkUrl = getLatestApkUrl(release)
                    val releaseDescription =
                        if (release == null) "" else release.tagName + "\n" + release.body

                    AlertDialog.Builder(
                        activity,
                    )
                        .setCancelable(true)
                        .setTitle(activity.getString(R.string.existsUpdate))
                        .setMessage(releaseDescription)
                        .setPositiveButton(activity.getString(R.string.update)) { _: DialogInterface?, _: Int ->
                            startUpdate(
                                activity,
                                apkUrl,
                            )
                        }
                        .setNeutralButton(activity.getString(R.string.ignoreUpdate)) { _: DialogInterface?, _: Int ->
                            AlertDialog.Builder(
                                activity,
                            )
                                .setTitle(activity.getString(R.string.guide))
                                .setMessage(activity.getString(R.string.guideIgnoreUpdate))
                                .setCancelable(false)
                                .setPositiveButton(activity.getString(R.string.confirm)) { _: DialogInterface?, _: Int ->
                                    doNotShow()
                                }
                                .show()
                        }
                        .setNegativeButton(activity.getString(R.string.close)) { _: DialogInterface?, _: Int -> }
                        .show()

                    permissionCheck(activity)
                    dialogLastCheck = System.currentTimeMillis()
                    ResponseCloser.closeAll(response)
                } catch (e: Exception) {
                    Log.w(AppUpdaterUtil::class.java.name, "error update dialog show", e)
                    AnalysisUtil.log("fail update")
                    AnalysisUtil.recordException(e)
                }
            }
        } catch (e: NullPointerException) {
            Log.w(AppUpdaterUtil::class.java.name, "activity is null", e)
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
                            Uri.parse("market://details?id=$appPackageName"),
                        ),
                    )
                    clearDoNotShow()
                    return
                } catch (e: ActivityNotFoundException) {
                    AnalysisUtil.log("play store is installed, but launch error")
                    AnalysisUtil.recordException(e)
                }
            }
            if (BuildConfig.BUILD_MODE != "playstore") {
                if (apkUrl.contains(".apk")) {
                    DownloadApk(context).startDownloadingApk(
                        apkUrl,
                        apkUrl.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()[
                            apkUrl.split("/".toRegex())
                                .dropLastWhile { it.isEmpty() }
                                .toTypedArray().size - 1,
                        ].replace(".apk", ""),
                    )
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                }
            } else {
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName"),
                    )
                context.startActivity(intent)
            }
            clearDoNotShow()
        } catch (e: Exception) {
            Log.w(AppUpdaterUtil::class.java.name, "fail update")
            AnalysisUtil.log("fail update")
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
                    return@withContext location.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()[
                        location.split("/".toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray().size - 1,
                    ]
                }
            } catch (e: Exception) {
                Log.w(AppUpdaterUtil::class.java.name, "getLatestVersion fail", e)
                AnalysisUtil.recordException(e)
            }
            return@withContext "1.0"
        }

    fun getLatestApkUrl(release: Release?): String {
        var defaultApkUrl =
            String.format(
                "https://github.com/%s/%s/releases/latest",
                RemoteConfigUtil.getString("repoOwner"),
                RemoteConfigUtil.getString("repoName"),
            )
        if (release?.assets == null || release.assets!!.isEmpty()) {
            return defaultApkUrl
        }
        release.assets!!.forEach {
            if (it.contentType.equals("application/vnd.android.package-archive")) {
                defaultApkUrl = it.downloadUrl ?: ""
            }
        }

        return defaultApkUrl
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
        var latestVersionNumber = 1.0f
        var currentVersionNumber = 1.0f
        val latestVersion = getLatestVersion()
        val currentVersion = getCurrentVersion(context)
        if (latestVersion.contains(".")) {
            latestVersionNumber =
                parseFloat(latestVersion.split("[.-]".toRegex())[0] + "." + latestVersion.split("[.-]".toRegex())[1])
        }
        if (currentVersion.contains(".")) {
            currentVersionNumber =
                parseFloat(currentVersion.split("[.-]".toRegex())[0] + "." + currentVersion.split("[.-]".toRegex())[1])
        }
        return latestVersion != "1.0" && currentVersionNumber < latestVersionNumber
    }

    private fun permissionCheck(activity: Activity): Boolean {
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
                AlertDialog.Builder(activity)
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
                    }
                    .setCancelable(false)
                    .show()
            }
        }
        return true
    }

    @Suppress("KotlinConstantConditions")
    suspend fun notification(context: Context) {
        if (BuildConfig.BUILD_MODE == "playstore") {
            return
        }
        if (abs(System.currentTimeMillis() - notificationLastCheck) < 5 * 60 * 1000 || isDoNotShow()) {
            return
        }
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
                NotificationCompat.Builder(context, "update_notification_channel")
                    .setContentIntent(contentIntent)
                    .setContentTitle(context.getString(R.string.updateAvailable))
                    .setContentText(context.getString(R.string.guideUpdateAvailable))
                    .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(0, notification)
        }
        notificationLastCheck = System.currentTimeMillis()
    }

    private fun isPlayStoreInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.android.vending", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
