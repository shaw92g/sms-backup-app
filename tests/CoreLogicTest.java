package com.example.smsbackup;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class CoreLogicTest {
    public static void main(String[] args) throws Exception {
        ZoneId zone = ZoneId.of("America/New_York");
        LocalDate spring = LocalDate.of(2026, 3, 8);
        LocalDate fall = LocalDate.of(2026, 11, 1);
        check(BackupDates.endMillis(spring, zone) - BackupDates.startMillis(spring, zone)
                == 23 * 60 * 60 * 1000L, "Spring day must be 23 hours");
        check(BackupDates.endMillis(fall, zone) - BackupDates.startMillis(fall, zone)
                == 25 * 60 * 60 * 1000L, "Fall day must be 25 hours");
        check(BackupDates.nextAlarm(Instant.parse("2026-10-27T04:30:00Z"), zone)
                .toInstant().equals(Instant.parse("2026-10-27T05:00:00Z")), "Before 1 AM");
        check(BackupDates.nextAlarm(Instant.parse("2026-10-27T05:00:00Z"), zone)
                .toInstant().equals(Instant.parse("2026-10-28T05:00:00Z")), "At 1 AM");
        check(BackupDates.nextAlarm(Instant.parse("2026-11-01T04:30:00Z"), zone)
                .toInstant().equals(Instant.parse("2026-11-01T05:00:00Z")), "DST overlap");
        check(BackupDates.nextAlarm(Instant.parse("2026-03-29T02:30:00Z"), ZoneId.of("Europe/London"))
                .toInstant().equals(Instant.parse("2026-03-30T00:00:00Z")),
                "A skipped 1 AM must not shift the following day's alarm to 2 AM");
        check(BackupDates.latestDueDay(Instant.parse("2026-10-27T04:30:00Z"), zone)
                .equals(LocalDate.of(2026, 10, 25)), "Do not upload yesterday before 1 AM");
        check(BackupDates.latestDueDay(Instant.parse("2026-10-27T05:00:00Z"), zone)
                .equals(LocalDate.of(2026, 10, 26)), "Yesterday becomes due at 1 AM");
        check(BackupDates.uploadDay("", LocalDate.of(2026, 10, 27))
                .equals(LocalDate.of(2026, 10, 26)), "Blank date defaults to yesterday");
        check(BackupDates.uploadDay("   ", LocalDate.of(2026, 1, 1))
                .equals(LocalDate.of(2025, 12, 31)), "Whitespace defaults across year boundary");
        check(BackupDates.uploadDay("", LocalDate.of(2026, 3, 1))
                .equals(LocalDate.of(2026, 2, 28)), "Default across month boundary");
        check(BackupDates.uploadDay(" 2024-02-29 ", LocalDate.of(2026, 10, 27))
                .equals(LocalDate.of(2024, 2, 29)), "Explicit leap date with surrounding spaces");
        for (String invalid : new String[]{"2026-02-29", "2026-04-31", "2026-13-01", "2026-1-2", "10/26/2026", "not-a-date"}) {
            boolean rejected = false;
            try {
                BackupDates.uploadDay(invalid, LocalDate.of(2026, 10, 27));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "Invalid date must be rejected: " + invalid);
        }
                EndpointPolicy.validate("https://example.com/sms", false);
                EndpointPolicy.validate("http://localhost:3000/sms", true);
                EndpointPolicy.validate("http://192.168.1.50:3000/sms", true);
                rejectEndpoint("http://localhost:3000/sms", false);
                rejectEndpoint("file:///tmp/sms", true);
                rejectEndpoint("https://user:password@example.com/sms", true);
                rejectEndpoint("https://example.com/sms#fragment", true);
                rejectEndpoint("http://localhost:99999/sms", true);
                System.out.println("Passed: calendar boundaries, manual dates, 1 AM scheduling, DST, and HTTP endpoint policy.");
    }

        private static void rejectEndpoint(String endpoint, boolean allowHttp) {
                try {
                        EndpointPolicy.validate(endpoint, allowHttp);
                } catch (IllegalArgumentException expected) {
                        return;
                }
                throw new AssertionError("Endpoint should be rejected: " + endpoint);
        }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}