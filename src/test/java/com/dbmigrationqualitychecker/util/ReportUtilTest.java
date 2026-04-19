package com.dbmigrationqualitychecker.util;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReportUtilTest {

    @Test
    void formatsZeroDuration() {
        Instant now = Instant.now();
        assertThat(ReportUtil.formatDuration(now, now)).isEqualTo("0m 0s 0ms");
    }

    @Test
    void formatsSubSecondDuration() {
        Instant start = Instant.ofEpochMilli(1_000L);
        Instant end = Instant.ofEpochMilli(1_250L);
        assertThat(ReportUtil.formatDuration(start, end)).isEqualTo("0m 0s 250ms");
    }

    @Test
    void formatsMinuteAndSecondBreakdown() {
        Instant start = Instant.ofEpochMilli(0L);
        Instant end = start.plus(Duration.ofSeconds(125)).plusMillis(42);
        assertThat(ReportUtil.formatDuration(start, end)).isEqualTo("2m 5s 42ms");
    }
}
