package com.dbmigrationqualitychecker.check.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValueComparatorTest {

    @Test
    void bothNullDoesNotDiffer() {
        assertThat(ValueComparator.differ(null, null)).isFalse();
    }

    @Test
    void oneNullDiffers() {
        assertThat(ValueComparator.differ(null, "x")).isTrue();
        assertThat(ValueComparator.differ("x", null)).isTrue();
    }

    @Test
    void identicalDoesNotDiffer() {
        assertThat(ValueComparator.differ("abc", "abc")).isFalse();
    }

    @Test
    void differentSameLengthDiffers() {
        assertThat(ValueComparator.differ("abc", "def")).isTrue();
    }

    @Test
    void trailingZerosArePaddingAndAreIgnored() {
        assertThat(ValueComparator.differ("2024-01-01 10:00:00", "2024-01-01 10:00:00.000000"))
                .isFalse();
    }

    @Test
    void trailingNonZerosAreMismatched() {
        assertThat(ValueComparator.differ("abc", "abcdef")).isTrue();
    }

    @Test
    void mismatchedPrefixWithZeroPaddingStillMismatches() {
        assertThat(ValueComparator.differ("abx", "abc000")).isTrue();
    }
}
