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

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String clean = value.replace("\u0000", "");
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    /** Empty or blank becomes null — used for optional notes. */
    public static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
