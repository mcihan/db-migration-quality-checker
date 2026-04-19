package com.dbmigrationqualitychecker.repository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UUIDConverterTest {

    @Test
    void roundTripsSingleUuid() {
        UUID original = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] bytes = UUIDConverter.asBytes(original);
        assertThat(bytes).hasSize(16);
        assertThat(UUIDConverter.fromBytes(bytes)).isEqualTo(original);
    }

    @Test
    void roundTripsListOfUuids() {
        List<UUID> uuids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<byte[]> asBytes = UUIDConverter.asBytesList(uuids);
        assertThat(asBytes).hasSameSizeAs(uuids);
        assertThat(UUIDConverter.fromBytes(asBytes)).containsExactlyElementsOf(uuids);
    }

    @Test
    void producesSixteenBytesForZeroUuid() {
        byte[] bytes = UUIDConverter.asBytes(new UUID(0, 0));
        assertThat(bytes).hasSize(16).containsOnly((byte) 0);
    }
}
