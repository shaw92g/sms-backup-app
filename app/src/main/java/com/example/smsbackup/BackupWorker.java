package com.example.smsbackup;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class BackupWorker extends Worker {
    private final Settings settings;
    private final String generation;

    public BackupWorker(Context context, WorkerParameters parameters) {
        super(context, parameters);
        settings = new Settings(context);
        generation = getInputData().getString("generation");
    }

    @Override public Result doWork() {
        if (!settings.active(generation) || isStopped()) return Result.success();
        if (getApplicationContext().checkSelfPermission(Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            settings.status(generation, "SMS permission is missing. Open the app to grant access.");
            return Result.failure();
        }
        String manualDay = getInputData().getString("manual_day");
        boolean manual = manualDay != null;
        ZoneId zone = manual ? ZoneId.of(getInputData().getString("zone")) : ZoneId.systemDefault();
        LocalDate day = LocalDate.parse(manual ? manualDay : settings.nextDay());
        LocalDate cutoff = manual ? day
            : BackupDates.latestDueDay(Instant.now(), zone);
        try {
            String endpoint = settings.endpoint();
            String token = settings.token();
            int processed = 0;
            while (!day.isAfter(cutoff) && !isStopped() && settings.active(generation)) {
                if (processed >= 7) return Result.retry();
                settings.status(generation, "Uploading " + day + "...");
                File json = File.createTempFile("sms-" + day + "-", ".json", getApplicationContext().getCacheDir());
                try {
                    int count = SmsExporter.export(getApplicationContext(), day, zone, json);
                    if (isStopped() || !settings.active(generation)) return Result.success();
                    int response = upload(endpoint, token, day, zone, json);
                    if (response < 200 || response >= 300) {
                        boolean retry = response == 408 || response == 429 || response >= 500;
                        settings.status(generation, "HTTP " + response + " for " + day
                                + (retry ? ". Will retry automatically." : ". Check endpoint and credentials."));
                        return retry ? Result.retry() : Result.failure();
                    }
                    String timestamp = Instant.now().atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"));
                    if (!settings.complete(generation, day, manual,
                            "Uploaded " + day + ": " + count + " SMS. Last success: " + timestamp + ".")) {
                        return settings.active(generation) ? Result.retry() : Result.success();
                    }
                } finally {
                    json.delete();
                }
                if (manual) break;
                day = day.plusDays(1);
                processed++;
            }
            return Result.success();
        } catch (SecurityException exception) {
            settings.status(generation, "SMS access was denied. Check app permissions.");
            return Result.failure();
        } catch (IOException exception) {
            settings.status(generation, "Connection or export failed. Will retry automatically.");
            return Result.retry();
        } catch (Exception exception) {
            settings.status(generation, "Cannot read saved credentials. Save your settings again.");
            return Result.failure();
        }
    }

    private int upload(String endpoint, String token, LocalDate day, ZoneId zone, File json) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(60_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(json.length());
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Disposition", "attachment; filename=\"sms-" + day + ".json\"");
            connection.setRequestProperty("Idempotency-Key", settings.installId() + ":" + day);
            connection.setRequestProperty("X-Backup-Date", day.toString());
            connection.setRequestProperty("X-Backup-Timezone", zone.getId());
            if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            try (FileInputStream input = new FileInputStream(json);
                 OutputStream output = connection.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (isStopped() || !settings.active(generation)) throw new IOException("Backup stopped");
                    output.write(buffer, 0, length);
                }
            }
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }
}