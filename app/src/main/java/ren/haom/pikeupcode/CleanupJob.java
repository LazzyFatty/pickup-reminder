package ren.haom.pikeupcode;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class CleanupJob extends JobService {
    private static final int ID = 410;
    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler.getPendingJob(ID) == null) scheduler.schedule(new JobInfo.Builder(ID, new ComponentName(context, CleanupJob.class))
                .setPeriodic(86400000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { ParcelStore.get(this).cleanup(System.currentTimeMillis()); jobFinished(params, false); }
            catch (RuntimeException e) { jobFinished(params, true); }
        }, "pickup-cleanup").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
