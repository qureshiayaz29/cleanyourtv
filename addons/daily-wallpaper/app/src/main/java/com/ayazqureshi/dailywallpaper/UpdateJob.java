package com.ayazqureshi.dailywallpaper;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * The background download: a JobScheduler job that Android runs every few hours while the TV has a network.
 * Most runs only look at the clock; one a day asks Bing for the feed. No service stays running in between.
 */
public final class UpdateJob extends JobService {
    private static final int JOB_ID = 1;
    private static final long PERIOD_MS = 6 * 60 * 60_000L;

    /** Registers the job (kept across a restart of the TV) while daily updates are on, and removes it otherwise. */
    static void schedule(Context context, boolean enabled) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) return;
            if (!enabled) {
                scheduler.cancel(JOB_ID);
                return;
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return;
            scheduler.schedule(new JobInfo.Builder(JOB_ID, new ComponentName(context, UpdateJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build());
        } catch (RuntimeException failure) {
            Log.w("DailyWallpaper", "Could not schedule the daily job: " + failure);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Context app = getApplicationContext();
        new Thread(() -> {
            Updater.tick(app);
            jobFinished(params, false);
        }, "daily-wallpaper-job").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
