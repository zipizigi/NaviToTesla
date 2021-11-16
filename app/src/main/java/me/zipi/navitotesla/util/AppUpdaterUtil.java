package me.zipi.navitotesla.util;

import android.content.Context;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.Display;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AppUpdaterUtil {

    public static void notification(final Context context) {
        new AppUpdater(context)
                .setDisplay(Display.NOTIFICATION)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("zipizigi", "NaviToTesla")
                .start();
    }

    public static void dialog(Context context) {
        new AppUpdater(context)
                .setDisplay(Display.DIALOG)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("zipizigi", "NaviToTesla")
                .start();
    }
}
