package me.zipi.navitotesla.service.share;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

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
        context.startActivity(intent);
    }
}
