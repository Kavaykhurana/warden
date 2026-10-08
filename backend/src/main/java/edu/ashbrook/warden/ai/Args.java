package edu.ashbrook.warden.ai;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import edu.ashbrook.warden.common.FieldValidationException;
import edu.ashbrook.warden.common.Text;

/** Parsing of model-supplied tool arguments. Every value is untrusted; failures become FAILED tool results. */
final class Args {

    private Args() {
    }

    /** Ordered argument map for events and audit; null values become empty strings. */
    static Map<String, String> of(String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return map;
    }

    static long wholeNumber(String name, String value) {
        try {
            return Long.parseLong(Text.trim(value).replaceFirst("^#", ""));
        } catch (NumberFormatException e) {
            throw new FieldValidationException(name, "Use a whole number.");
        }
    }

    static boolean yesNo(String name, String value) {
        return switch (Text.trim(value).toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new FieldValidationException(name, "Use true or false.");
        };
    }

    static LocalDate date(String name, String value) {
        try {
            return LocalDate.parse(Text.trim(value));
        } catch (DateTimeParseException e) {
            throw new FieldValidationException(name, "Use the format YYYY-MM-DD.");
        }
    }

    static int hours(String value) {
        String clean = Text.trim(value);
        if (clean.isEmpty()) {
            return 24;
        }
        // Clamp so an absurd value stays out of range (and is rejected by the service) instead of overflowing into it.
        return (int) Math.clamp(wholeNumber("hours", clean), Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
