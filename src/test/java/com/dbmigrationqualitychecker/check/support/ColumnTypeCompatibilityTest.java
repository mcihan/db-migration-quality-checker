package com.dbmigrationqualitychecker.check.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ColumnTypeCompatibilityTest {

    @Test
    void typesMatch_exactEquality() {
        assertThat(ColumnTypeCompatibility.typesMatch("VARCHAR", "VARCHAR")).isTrue();
    }

    @Test
    void typesMatch_intFamily() {
        assertThat(ColumnTypeCompatibility.typesMatch("INTEGER", "BIGINT")).isTrue();
        assertThat(ColumnTypeCompatibility.typesMatch("INT", "SMALLINT")).isTrue();
    }

    @Test
    void typesMatch_timestampFamily() {
        assertThat(ColumnTypeCompatibility.typesMatch("TIMESTAMP", "TIMESTAMP(6)")).isTrue();
    }

    @Test
    void typesMatch_characterAgainstCharAndBinary() {
        assertThat(ColumnTypeCompatibility.typesMatch("CHARACTER", "CHAR")).isTrue();
        assertThat(ColumnTypeCompatibility.typesMatch("CHARACTER", "VARBINARY")).isTrue();
        assertThat(ColumnTypeCompatibility.typesMatch("VARCHAR", "BINARY")).isTrue();
    }

    @Test
    void typesMatch_bitDataAgainstBinary() {
        assertThat(ColumnTypeCompatibility.typesMatch("CHAR FOR BIT DATA", "VARBINARY")).isTrue();
    }

    @Test
    void typesMatch_unrelatedTypesDoNotMatch() {
        assertThat(ColumnTypeCompatibility.typesMatch("VARCHAR", "DECIMAL")).isFalse();
    }

    @Test
    void defaultsMatch_bothNullIsMatch() {
        assertThat(ColumnTypeCompatibility.defaultsMatch(null, null)).isTrue();
    }

    @Test
    void defaultsMatch_oneNullIsMismatch() {
        assertThat(ColumnTypeCompatibility.defaultsMatch(null, "X")).isFalse();
        assertThat(ColumnTypeCompatibility.defaultsMatch("X", null)).isFalse();
    }

    @Test
    void defaultsMatch_quotedZeroVsHex() {
        assertThat(ColumnTypeCompatibility.defaultsMatch("'0'", "0X30")).isTrue();
    }

    @Test
    void defaultsMatch_timestampDefaults() {
        assertThat(ColumnTypeCompatibility.defaultsMatch("CURRENT_TIMESTAMP", "TIMESTAMP WITHOUT TIME ZONE"))
                .isTrue();
    }
}
