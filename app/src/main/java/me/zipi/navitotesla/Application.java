package me.zipi.navitotesla;

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
        TokenWorker.startBackgroundWork(this);
    }


    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this);
    }

    public AppRepository getRepository() {
        return AppRepository.getInstance(this, getDatabase());
    }
}
