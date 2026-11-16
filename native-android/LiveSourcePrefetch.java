package com.v3x.zekify;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class LiveSourcePrefetch {
    private static final String PERIODIC = "zekify-live-source-prefetch-periodic";
    private static final String IMMEDIATE = "zekify-live-source-prefetch-now";
    private LiveSourcePrefetch() {}

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();
        WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
            LiveSourcePrefetchWorker.class, 15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build();
        manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic);
        OneTimeWorkRequest immediate = new OneTimeWorkRequest.Builder(LiveSourcePrefetchWorker.class)
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build();
        manager.enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.KEEP, immediate);
    }
}
