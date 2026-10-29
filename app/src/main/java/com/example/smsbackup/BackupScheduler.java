package com.example.smsbackup;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

public final class BackupScheduler {
    static final String ACTION_BACKUP = "com.example.smsbackup.BACKUP";
    private static final String WORK_TAG = "sms_backup";

    private BackupScheduler() {}

    public static boolean exactAllowed(Context context) {
        return Build.VERSION.SDK_INT < 31
                || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    public static void schedule(Context context) {
        if (!new Settings(context).enabled()) return;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        long time = BackupDates.nextAlarm(Instant.now(), ZoneId.systemDefault()).toInstant().toEpochMilli();
        try {
            if (exactAllowed(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, alarm(context));
                return;
            }
        } catch (SecurityException ignored) {
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, alarm(context));
    }

    public static void enqueue(Context context, LocalDate manualDay) {
        Settings settings = new Settings(context);
        if (!settings.enabled()) return;
        Data.Builder data = new Data.Builder().putString("generation", settings.generation());
        if (manualDay != null) data.putString("manual_day", manualDay.toString())
                .putString("zone", ZoneId.systemDefault().getId());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(BackupWorker.class)
                .setInputData(data.build()).addTag(WORK_TAG)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        String name = "sms_backup_" + settings.generation()
                + (manualDay == null ? "_automatic" : "_manual_" + manualDay);
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request);
    }

    public static void cancel(Context context) {
        context.getSystemService(AlarmManager.class).cancel(alarm(context));
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG);
    }

    private static PendingIntent alarm(Context context) {
        return PendingIntent.getBroadcast(context, 0,
                new Intent(context, BackupReceiver.class).setAction(ACTION_BACKUP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}