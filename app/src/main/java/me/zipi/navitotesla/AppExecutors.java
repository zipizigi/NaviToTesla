package me.zipi.navitotesla;

import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AppExecutors {
    private static final Executor executor = new ThreadPoolExecutor(0, 50, 30, TimeUnit.SECONDS, new SynchronousQueue<>());


    private AppExecutors() {
    }

    public static void execute(Runnable command) {
        executor.execute(command);
    }

}
