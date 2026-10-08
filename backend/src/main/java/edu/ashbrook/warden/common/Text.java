package edu.ashbrook.warden.common;

/** Small string helpers shared by services. */
public final class Text {

    private Text() {
    }

    public static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    public static boolean lengthBetween(String trimmed, int min, int max) {
        return trimmed.length() >= min && trimmed.length() <= max;
    }

    /** At most {@code max} UTF-16 units, cut on a character boundary; NUL characters removed. */
    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String clean = value.replace("\u0000", "");
        if (clean.length() <= max) {
            return clean;
        }
        // Never keep half of a surrogate pair (emoji): it can't be encoded and would be stored as '?'.
        int end = Character.isHighSurrogate(clean.charAt(max - 1)) ? max - 1 : max;
        return clean.substring(0, end);
    }

    /** Empty or blank becomes null — used for optional notes. */
    public static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
