package com.example.smsbackup;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SmsExporter {
    private SmsExporter() {}

    public static int export(Context context, LocalDate day, ZoneId zone, File file) throws IOException {
        String[] columns = {"_id", "address", "date", "body"};
        String selection = "date >= ? AND date < ?";
        String[] args = {Long.toString(BackupDates.startMillis(day, zone)),
                Long.toString(BackupDates.endMillis(day, zone))};
        int count = 0;
        try (Cursor cursor = context.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                columns, selection, args, "date ASC, _id ASC");
             Writer output = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
             SmsJsonWriter writer = new SmsJsonWriter(output, day, zone)) {
            if (cursor == null) throw new IOException("SMS provider unavailable");
            while (cursor.moveToNext()) {
                writer.message(cursor.getString(0), cursor.getString(1), cursor.getLong(2),
                        cursor.getString(3));
                count++;
            }
        }
        return count;
    }
}