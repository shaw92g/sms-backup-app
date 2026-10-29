package com.example.smsbackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BackupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!new Settings(context).enabled()) return;
        BackupScheduler.schedule(context);
        BackupScheduler.enqueue(context, null);
    }
}