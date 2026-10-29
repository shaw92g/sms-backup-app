package com.example.smsbackup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class SmsJsonWriterTest {
    private final LocalDate day = LocalDate.of(2026, 10, 26);
    private final ZoneId zone = ZoneId.of("America/New_York");

    @Test public void preservesMultilineMessageAndTypedFields() throws Exception {
        String body = "First line\nSecond line\r\n\"quotes\", comma, \\ slash\t\u20ac \ud83d\udcf1\u0001";
        String address = "Sender\n\"name\"";
        long received = Instant.parse("2026-10-26T12:00:00Z").toEpochMilli();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (SmsJsonWriter writer = new SmsJsonWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8), day, zone)) {
            writer.message("42", address, received, body);
            writer.message("43", null, received + 1, null);
            writer.message("44", "", received + 2, "");
        }
        String encoded = output.toString(StandardCharsets.UTF_8);
        assertTrue(encoded.contains("\\n"));
        assertFalse(encoded.contains("\n"));
        JsonObject backup = JsonParser.parseString(encoded).getAsJsonObject();
        assertEquals(day.toString(), backup.get("backup_date").getAsString());
        assertEquals(zone.getId(), backup.get("timezone").getAsString());
        JsonArray messages = backup.getAsJsonArray("messages");
        assertEquals(3, messages.size());
        for (int index = 0; index < messages.size(); index++) {
            assertEquals(Set.of("sms_id", "address", "received_at", "received_at_epoch_ms", "body"),
                messages.get(index).getAsJsonObject().keySet());
        }
        JsonObject first = messages.get(0).getAsJsonObject();
        assertEquals("42", first.get("sms_id").getAsString());
        assertEquals(address, first.get("address").getAsString());
        assertEquals(body, first.get("body").getAsString());
        assertEquals("2026-10-26T08:00-04:00", first.get("received_at").getAsString());
        assertTrue(first.getAsJsonPrimitive("received_at_epoch_ms").isNumber());
        assertEquals(received, first.get("received_at_epoch_ms").getAsLong());
        JsonObject second = messages.get(1).getAsJsonObject();
        for (String field : new String[]{"address", "body"}) {
            assertTrue(second.get(field).isJsonNull());
        }
        assertEquals("", messages.get(2).getAsJsonObject().get("body").getAsString());
    }

    @Test public void writesValidEmptyDay() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (SmsJsonWriter writer = new SmsJsonWriter(
                new OutputStreamWriter(output, StandardCharsets.UTF_8), day, zone)) {
        }
        JsonObject backup = JsonParser.parseString(output.toString(StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(day.toString(), backup.get("backup_date").getAsString());
        assertEquals(zone.getId(), backup.get("timezone").getAsString());
        assertEquals(0, backup.getAsJsonArray("messages").size());
    }
}