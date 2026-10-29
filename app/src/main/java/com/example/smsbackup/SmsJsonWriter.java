package com.example.smsbackup;

import com.google.gson.stream.JsonWriter;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class SmsJsonWriter implements Closeable {
    private final JsonWriter writer;
    private final ZoneId zone;

    public SmsJsonWriter(Writer output, LocalDate day, ZoneId zone) throws IOException {
        this.zone = zone;
        writer = new JsonWriter(output);
        writer.setSerializeNulls(true);
        writer.beginObject();
        writer.name("backup_date").value(day.toString());
        writer.name("timezone").value(zone.getId());
        writer.name("messages").beginArray();
    }

    public void message(String smsId, String address, long receivedAt, String body) throws IOException {
        writer.beginObject();
        writer.name("sms_id").value(smsId);
        writer.name("address").value(address);
        writer.name("received_at").value(Instant.ofEpochMilli(receivedAt)
                .atZone(zone).toOffsetDateTime().toString());
        writer.name("received_at_epoch_ms").value(receivedAt);
        writer.name("body").value(body);
        writer.endObject();
    }

    @Override public void close() throws IOException {
        try {
            writer.endArray();
            writer.endObject();
        } finally {
            writer.close();
        }
    }
}