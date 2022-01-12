package me.zipi.navitotesla;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import me.zipi.navitotesla.background.TokenWorker;
import me.zipi.navitotesla.db.AppDatabase;
import me.zipi.navitotesla.util.AnalysisUtil;
import me.zipi.navitotesla.util.RemoteConfigUtil;

public class Application extends android.app.Application {


    @Override
    public void onCreate() {
        super.onCreate();
        getRepository();

        RemoteConfigUtil.initialize();
        AnalysisUtil.initialize(this);
        if (!BuildConfig.DEBUG) {
            TokenWorker.startBackgroundWork(this);
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false);
        }
    }


    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this);
    }

    public AppRepository getRepository() {
        return AppRepository.getInstance(this, getDatabase());
    }
}
