package com.dbmigrationqualitychecker.check.support;

/**
 * Compares two stringified column values while ignoring differences that we
 * know are irrelevant to migration correctness — for instance,
 * {@code "2024-01-01 10:00:00"} vs {@code "2024-01-01 10:00:00.000000"}: the
 * fractional-second zero padding is added by some engines on read-out.
 */
public final class ValueComparator {

    private ValueComparator() {}

    /** @return true if the two values differ in a way that should be reported as a mismatch. */
    public static boolean differ(String source, String target) {
        if (source == null && target == null) {
            return false;
        }
        if (source == null || target == null) {
            return true;
        }
        if (source.length() == target.length()) {
            return !source.equals(target);
        }
        int min = Math.min(source.length(), target.length());
        if (!source.substring(0, min).equals(target.substring(0, min))) {
            return true;
        }
        return !isFractionalZeroPadding(source.substring(min))
                || !isFractionalZeroPadding(target.substring(min));
    }

    /**
     * Recognise trailing zero padding that some engines add on read-out —
     * e.g. {@code ".000000"} after a timestamp. An empty suffix is trivially
     * "all padding". The optional leading {@code '.'} is the
     * fractional-seconds separator.
     */
    private static boolean isFractionalZeroPadding(String suffix) {
        if (suffix.isEmpty()) {
            return true;
        }
        String body = suffix.charAt(0) == '.' ? suffix.substring(1) : suffix;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
