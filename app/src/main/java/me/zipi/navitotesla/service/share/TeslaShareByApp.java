package me.zipi.navitotesla.service.share;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import me.zipi.navitotesla.util.AnalysisUtil;


public class TeslaShareByApp extends TeslaShareBase implements TeslaShare {
    public TeslaShareByApp(@NonNull Context context) {
        super(context);
    }

    @Override
    public void share(String address) {
        AnalysisUtil.log("share using tesla app share");
        Intent intent = new Intent();
        intent.setAction("android.intent.action.SEND");
        intent.setComponent(new ComponentName("com.teslamotors.tesla", "com.tesla.share.ShareActivity"));
        intent.setType("text/plain");
        intent.putExtra("android.intent.extra.TEXT", address);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            AnalysisUtil.logEvent("share_by_app_success", new Bundle());
        } catch (ActivityNotFoundException e) {
            AnalysisUtil.log("Tesla app is not installed");
            AnalysisUtil.logEvent("share_by_app_fail", new Bundle());
        }
    }
}
