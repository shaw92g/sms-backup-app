package com.example.smsbackup;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class BackupDates {
    private BackupDates() {}

    public static LocalDate uploadDay(String input, LocalDate today) {
        String value = input.trim();
        if (value.isEmpty()) return today.minusDays(1);
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("Enter a date as YYYY-MM-DD.");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Enter a valid calendar date.", exception);
        }
    }

    public static ZonedDateTime nextAlarm(Instant now, ZoneId zone) {
        ZonedDateTime local = now.atZone(zone);
        ZonedDateTime alarm = local.toLocalDate().atTime(1, 0).atZone(zone);
        return alarm.toInstant().isAfter(now) ? alarm
            : local.toLocalDate().plusDays(1).atTime(1, 0).atZone(zone);
    }

    public static long startMillis(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static LocalDate latestDueDay(Instant now, ZoneId zone) {
        return nextAlarm(now, zone).toLocalDate().minusDays(2);
    }

    public static long endMillis(LocalDate day, ZoneId zone) {
        return startMillis(day.plusDays(1), zone);
    }
}