package me.zipi.navitotesla;

import me.zipi.navitotesla.db.AppDatabase;

public class Application extends android.app.Application {


    @Override
    public void onCreate() {
        super.onCreate();
        getRepository();
    }


    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this);
    }

    public AppRepository getRepository() {
        return AppRepository.getInstance(this, getDatabase());
    }
}
