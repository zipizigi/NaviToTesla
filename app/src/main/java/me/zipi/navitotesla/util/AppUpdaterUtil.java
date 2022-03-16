package me.zipi.navitotesla.util;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.dcastalia.localappupdate.DownloadApk;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.zipi.navitotesla.BuildConfig;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.api.GithubApi;
import me.zipi.navitotesla.model.Github;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppUpdaterUtil {
    private static final GithubApi githubApi = new Retrofit.Builder()
            .baseUrl("https://api.github.com")
            .addConverterFactory(GsonConverterFactory.create())
            .client(new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(new HttpRetryInterceptor(10))
                    .build())
            .build().create(GithubApi.class);

    private static long dialogLastCheck = 0L;
    private static long notificationLastCheck = 0L;


    public static void clearDoNotShow(Context context) {
        PreferencesUtil.remove(context, "updateDoNotShow");
    }

    private static void doNotShow(Context context) {
        Long until = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L;
        PreferencesUtil.put(context, "updateDoNotShow", until);
    }

    private static boolean isDoNotShow(Context context) {
        Long until = PreferencesUtil.getLong(context, "updateDoNotShow", 0L);
        return System.currentTimeMillis() - until < 0;
    }

    public static void dialog(Activity activity) {
        dialog(activity, false);
    }

    public static void dialog(Activity activity, boolean isForce) {
        //noinspection ConstantConditions
        if (BuildConfig.BUILD_MODE.equals("playstore")) {
            return;
        }
        try {
            if (!isForce && (Math.abs(System.currentTimeMillis() - dialogLastCheck) < 5 * 60 * 1000 || isDoNotShow(activity))) {
                return;
            }
            if (isUpdateAvailable(activity)) {
                if (!permissionCheck(activity)) {
                    return;
                }
                try {
                    Github.Release release = null;

                    Response<List<Github.Release>> response =
                            githubApi.getReleases(RemoteConfigUtil.getConfig("repoOwner"), RemoteConfigUtil.getConfig("repoName"))
                                    .execute();
                    if (response.code() == 403) {
                        AnalysisUtil.log("github api rate limit exceed");
                    } else if (!response.isSuccessful() || response.body() == null) {
                        AnalysisUtil.log("github api call fail. Http code: " + response.code());
                        if (response.errorBody() != null) {
                            AnalysisUtil.log(response.errorBody().string());
                            AnalysisUtil.recordException(new RuntimeException());
                        }
                    } else {
                        for (Github.Release r : response.body()) {
                            if (!r.getIsPreRelease()) {
                                release = r;
                                break;
                            }
                        }
                    }


                    final String apkUrl = getLatestApkUrl(release);
                    final String releaseDescription = release == null ? "" : release.getTagName() + "\n" + release.getBody();
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setCancelable(true)
                            .setTitle(activity.getString(R.string.existsUpdate))
                            .setMessage(releaseDescription)
                            .setPositiveButton(activity.getString(R.string.update), (dialog, which) -> startUpdate(activity, apkUrl))
                            .setNeutralButton(activity.getString(R.string.ignoreUpdate), (dialog, which) -> new AlertDialog.Builder(activity)
                                    .setTitle(activity.getString(R.string.guide))
                                    .setMessage(activity.getString(R.string.guideIgnoreUpdate))
                                    .setCancelable(false)
                                    .setPositiveButton(activity.getString(R.string.confirm), (d, w) -> doNotShow(activity))
                                    .show())
                            .setNegativeButton(activity.getString(R.string.close), (dialog, which) -> {
                            })
                            .show());

                    permissionCheck(activity);
                    dialogLastCheck = System.currentTimeMillis();
                    ResponseCloser.closeAll(response);
                } catch (Exception e) {
                    Log.w(AppUpdaterUtil.class.getName(), "error update dialog show", e);
                    AnalysisUtil.log("fail update");
                    AnalysisUtil.recordException(e);
                }

            }
        } catch (NullPointerException e) {
            Log.w(AppUpdaterUtil.class.getName(), "activity is null", e);
        }
    }

    private static void startUpdate(Context context, String apkUrl) {
        try {
            AnalysisUtil.log("Start update app");
            if (isPlayStoreInstalled(context)) {
                final String appPackageName = context.getPackageName();
                try {
                    AnalysisUtil.log("Start update app - open play store");
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                    return;
                } catch (android.content.ActivityNotFoundException e) {
                    AnalysisUtil.log("play store is installed, but launch error");
                    AnalysisUtil.recordException(e);
                }
            }


            //noinspection ConstantConditions
            if (!BuildConfig.BUILD_MODE.equals("playstore")) {
                if (apkUrl.contains(".apk")) {
                    new DownloadApk(context).startDownloadingApk(apkUrl, apkUrl.split("/")[apkUrl.split("/").length - 1].replace(".apk", ""));
                } else {
                    context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
                }
            }
            clearDoNotShow(context);
        } catch (Exception e) {
            Log.w(AppUpdaterUtil.class.getName(), "fail update");
            AnalysisUtil.log("fail update");
            AnalysisUtil.recordException(e);
        }
    }

    public static String getLatestVersion() {
        try {
            String latestUrl = String.format("https://github.com/%s/%s/releases/latest",
                    RemoteConfigUtil.getConfig("repoOwner"), RemoteConfigUtil.getConfig("repoName"));
            HttpURLConnection con = (HttpURLConnection) (new URL(latestUrl).openConnection());
            con.setInstanceFollowRedirects(false);
            con.connect();
            if (con.getResponseCode() == 302 || con.getResponseCode() == 304) {
                String location = con.getHeaderField("Location");
                return location.split("/")[location.split("/").length - 1];
            }
            return "1.0";
        } catch (Exception e) {
            Log.w(AppUpdaterUtil.class.getName(), "getLatestVersion fail", e);
            AnalysisUtil.recordException(e);
            return "1.0";
        }
    }

    public static String getLatestApkUrl(Github.Release release) {
        String apkUrl = String.format("https://github.com/%s/%s/releases/latest",
                RemoteConfigUtil.getConfig("repoOwner"), RemoteConfigUtil.getConfig("repoName"));
        if (release == null || release.getAssets() == null || release.getAssets().size() == 0) {
            return apkUrl;
        }

        for (int i = 0; i < release.getAssets().size(); i++) {
            if (release.getAssets().get(i).getContentType().equals("application/vnd.android.package-archive")) {
                apkUrl = release.getAssets().get(i).getDownloadUrl();
            }
        }

        return apkUrl;
    }


    public static String getCurrentVersion(Context context) {
        String version = "1.0";
        if (context == null || context.getPackageManager() == null) {
            return version;
        }
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            AnalysisUtil.recordException(e);
        }
        return version;
    }

    public static boolean isUpdateAvailable(Context context) {
        float latestVersionNumber = 1.0f;
        float currentVersionNumber = 1.0f;
        String latestVersion = getLatestVersion();
        String currentVersion = getCurrentVersion(context);
        if (latestVersion.contains(".")) {
            latestVersionNumber = Float.parseFloat(latestVersion.split("[.-]")[0] + "." + latestVersion.split("[.-]")[1]);
        }
        if (currentVersion.contains(".")) {
            currentVersionNumber = Float.parseFloat(currentVersion.split("[.-]")[0] + "." + currentVersion.split("[.-]")[1]);
        }
        return !latestVersion.equals("1.0") && currentVersionNumber < latestVersionNumber;
    }

    private static boolean permissionCheck(Activity activity) {
        boolean granted = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(R.string.grantPermission))
                    .setMessage(activity.getString(R.string.guideGrantStoragePermission))
                    .setPositiveButton(activity.getString(R.string.confirm), (dialog, which) ->
                            activity.requestPermissions(new String[]
                                            {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                                    2)
                    )
                    .setCancelable(false)
                    .show());
        }
        return granted;
    }

    static public void notification(Context context) {
        //noinspection ConstantConditions
        if (BuildConfig.BUILD_MODE.equals("playstore")) {
            return;
        }
        if (Math.abs(System.currentTimeMillis() - notificationLastCheck) < 5 * 60 * 1000 || isDoNotShow(context)) {
            return;
        }

        if (isUpdateAvailable(context)) {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel mChannel = new NotificationChannel(
                        "update_notification_channel", "Update notification",
                        NotificationManager.IMPORTANCE_LOW);
                notificationManager.createNotificationChannel(mChannel);
            }

            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification notification = new NotificationCompat.Builder(context, "update_notification_channel")
                    .setContentIntent(contentIntent)
                    .setContentTitle(context.getString(R.string.updateAvailable))
                    .setContentText(context.getString(R.string.guideUpdateAvailable))
                    .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build();

            notificationManager.notify(0, notification);
        }
        notificationLastCheck = System.currentTimeMillis();
    }

    private static boolean isPlayStoreInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.android.vending", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

}
