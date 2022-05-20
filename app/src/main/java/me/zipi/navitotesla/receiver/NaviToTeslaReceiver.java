package me.zipi.navitotesla.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import me.zipi.navitotesla.AppExecutors;
import me.zipi.navitotesla.R;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.EnablerUtil;

public class NaviToTeslaReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        AnalysisUtil.log("receive NaviToTesla broadcast: " + action);

        final Context applicationContext = context.getApplicationContext();
        if (action.equalsIgnoreCase("navitotesla.ENABLE")) {
            AppExecutors.execute(() -> EnablerUtil.setAppEnabled(applicationContext, true));
            AnalysisUtil.makeToast(applicationContext, applicationContext.getString(R.string.enabledApp));
        } else if (action.equalsIgnoreCase("navitotesla.DISABLE")) {
            AppExecutors.execute(() -> EnablerUtil.setAppEnabled(applicationContext, false));
            AnalysisUtil.makeToast(applicationContext, applicationContext.getString(R.string.disabledApp));
        }
    }
}
