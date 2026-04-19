package com.dbmigrationqualitychecker.repository;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Kindly borrowed from liquidity-utils

/**
 * Utility class that is used to store UUID in a DB using its binary
 * representation.
 */
public final class UUIDConverter {

    private UUIDConverter() {
    }

    /**
     * Convert a UUID into its raw byte format.
     *
     * @param uuid that will be converted to its raw byte format.
     * @return uuid in its raw byte format.
     */
    public static byte[] asBytes(UUID uuid) {
        ByteBuffer bb = ByteBuffer.allocate(16);
        bb.putLong(uuid.getMostSignificantBits()).putLong(uuid.getLeastSignificantBits());
        return bb.array();
    }

    /**
     * Convert a uuid byte[] into an actual UUID object
     *
     * @param uuidBytes that will be converted to its UUID format.
     * @return UUID that is reconstituted from its raw byte format.
     */
    public static UUID fromBytes(byte[] uuidBytes) {
        ByteBuffer bb = ByteBuffer.wrap(uuidBytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    public static List<byte[]> asBytesList(List<UUID> uuids) {
        List<byte[]> uuidsAsBytes = new ArrayList<>();

        for (UUID id : uuids) {
            uuidsAsBytes.add(asBytes(id));
        }

        return uuidsAsBytes;
    }

    public static List<UUID> fromBytes(List<byte[]> uuidsAsBytes) {
        List<UUID> uuids = new ArrayList<>();

        for (byte[] uuidAsByte : uuidsAsBytes) {
            uuids.add(fromBytes(uuidAsByte));
        }

        return uuids;
    }

}
