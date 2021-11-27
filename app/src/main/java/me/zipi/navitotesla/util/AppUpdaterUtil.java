package me.zipi.navitotesla.util;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.Display;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

import androidx.appcompat.app.AlertDialog;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppUpdaterUtil {

    public static void notification(final Context context) {
        appUpdater(context, Display.NOTIFICATION);
    }

    public static void dialog(Context context) {
        appUpdater(context, Display.DIALOG);
    }

    private static void appUpdater(Context context, Display display) {
        new AppUpdater(context)
                .setDisplay(display)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("zipizigi", "NaviToTesla")

                .setTitleOnUpdateAvailable("업데이트가 있습니다")
                .setContentOnUpdateAvailable("새로운 업데이트가 있습니다. 아래 버튼을 눌러 업데이트 해주세요.")
                .setButtonUpdate("업데이트")
                .setButtonDismiss("닫기")
                .setButtonDoNotShowAgain("업데이트 무시")
                .setButtonDoNotShowAgainClickListener((d, w) ->
                        new AlertDialog.Builder(context)
                                .setTitle("안내")
                                .setMessage("업데이트를 무시한 후 다시 업데이트 안내를 받고 싶다면 '주소 캐시 삭제' 버튼을 눌러주세요.")
                                // .setIcon(R.drawable.ic_launcher_background)
                                .setCancelable(false)
                                .setPositiveButton("확인", (dialog, which) -> {
                                })
                                .show())
                .start();
    }
}
