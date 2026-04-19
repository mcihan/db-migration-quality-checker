package com.dbmigrationqualitychecker.util;

import java.time.Duration;
import java.time.Instant;

public class ReportUtil {
    public static String formatDuration(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        long minutes = duration.toMinutes();
        long seconds = duration.getSeconds() % 60;
        long milliseconds = duration.toMillis() % 1000;

        return String.format("%dm %ds %dms", minutes, seconds, milliseconds);
    }
}
