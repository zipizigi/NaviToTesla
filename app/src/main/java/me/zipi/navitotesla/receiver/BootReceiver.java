package me.zipi.navitotesla.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;

import me.zipi.navitotesla.util.AppUpdaterUtil;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            new Handler().postDelayed(() -> AppUpdaterUtil.notification(context), 30000);
        }
    }

}