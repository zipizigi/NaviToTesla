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
import android.preference.PreferenceManager;
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
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build())
            .build().create(GithubApi.class);

    private static long dialogLastCheck = 0L;
    private static long notificationLastCheck = 0L;


    public static void clearDoNotShow(Context context) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean("prefAppUpdaterShow", true).apply();

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

                    Response<List<Github.Release>> response = githubApi.getReleases("zipizigi", "NaviToTesla").execute();
                    if (response.code() == 403) {
                        AnalysisUtil.log("github api rate limit exceed");
                    } else if (!response.isSuccessful() || response.body() == null) {
                        AnalysisUtil.log("github api call fail. Http code: " + response.code());
                        if (response.errorBody() != null) {
                            AnalysisUtil.log(response.errorBody().string());
                            AnalysisUtil.recordException(new RuntimeException());
                        }
                    } else {
                        if (response.body().size() > 0) {
                            release = response.body().get(0);
                        }
                    }


                    final String apkUrl = getLatestApkUrl(release);
                    final String releaseDescription = release == null ? "" : release.getTagName() + "\n" + release.getBody();
                    activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                            .setCancelable(true)
                            .setTitle("업데이트가 있습니다.")
                            .setMessage(releaseDescription)
                            .setPositiveButton("업데이트", (dialog, which) -> startUpdate(activity, apkUrl))
                            .setNeutralButton("7일동안무시", (dialog, which) -> new AlertDialog.Builder(activity)
                                    .setTitle("안내")
                                    .setMessage("업데이트를 무시한 후 다시 업데이트 안내를 받고 싶다면 '주소 캐시 삭제' 버튼을 눌러주세요.")
                                    .setCancelable(false)
                                    .setPositiveButton("확인", (d, w) -> doNotShow(activity))
                                    .show())
                            .setNegativeButton("닫기", (dialog, which) -> {
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

    public static void startUpdate(Context context, String apkUrl) {
        try {
            if (apkUrl.contains(".apk")) {
                new DownloadApk(context).startDownloadingApk(apkUrl, apkUrl.split("/")[apkUrl.split("/").length - 1].replace(".apk", ""));
            } else {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)));
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
            HttpURLConnection con = (HttpURLConnection) (new URL("https://github.com/zipizigi/NaviToTesla/releases/latest").openConnection());
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
        String apkUrl = "https://github.com/zipizigi/NaviToTesla/releases/latest";
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
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception e) {
            AnalysisUtil.recordException(e);
        }
        return version;
    }

    public static boolean isUpdateAvailable(Context context) {
        String latestVersion = getLatestVersion();
        return !latestVersion.equals("1.0") && !getCurrentVersion(context).equals(latestVersion);
    }

    private static boolean permissionCheck(Activity activity) {
        boolean granted = activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                    .setTitle("권한 요청")
                    .setMessage("Navi To Tesla를 이용하려면 저장소 접근 권한이 필요합니다.\nNavi To Telsa에 권한을 허용해주세요.")
                    .setPositiveButton("확인", (dialog, which) ->
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
                    .setContentTitle("업데이트 가능")
                    .setContentText("Navi To Tesla 업데이트가 가능합니다")
                    .setSmallIcon(R.drawable.ic_baseline_system_update_24)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build();

            notificationManager.notify(0, notification);
        }
        notificationLastCheck = System.currentTimeMillis();
    }

}
