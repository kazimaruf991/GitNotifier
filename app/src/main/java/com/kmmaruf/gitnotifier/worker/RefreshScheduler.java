package com.kmmaruf.gitnotifier.worker;

import android.content.Context;

import androidx.preference.PreferenceManager;
import androidx.work.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RefreshScheduler {
    public static UUID schedulePeriodic(Context ctx, boolean shouldReplace) {
        String interval = PreferenceManager.getDefaultSharedPreferences(ctx).getString("pref_interval", "3600000");

        long millis = Long.parseLong(interval);

        String workName = "git_refresh";

        Data inputData = new Data.Builder().putString("work_name", workName).build();

        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(RepoWorker.class, millis, TimeUnit.MILLISECONDS).setInputData(inputData).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();

        ExistingPeriodicWorkPolicy policy = shouldReplace ? ExistingPeriodicWorkPolicy.REPLACE : ExistingPeriodicWorkPolicy.KEEP;

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(workName, policy, work);
        return work.getId();
    }

    public static UUID scheduleOneTime(Context ctx, int repoId) {

        Data inputData = new Data.Builder().putInt("key_repo_id", repoId).build();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(RepoWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(inputData).build();

        WorkManager.getInstance(ctx).enqueue(work);
        return work.getId();
    }

    public static UUID scheduleOneTimeAll(Context ctx) {

        Data inputData = new Data.Builder().putInt("key_repo_id", -2).build();

        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(RepoWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setInputData(inputData).build();

        WorkManager.getInstance(ctx).enqueue(work);

        return work.getId();
    }

    public static void cancelScheduledPeriodic(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork("git_refresh");
    }

}