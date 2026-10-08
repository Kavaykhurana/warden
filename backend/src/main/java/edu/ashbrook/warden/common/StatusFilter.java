package edu.ashbrook.warden.common;

/** The contract's {@code ?status=PENDING|ALL} query parameter. */
public final class StatusFilter {

    private StatusFilter() {
    }

    public static boolean pendingOnly(String status) {
        return switch (status) {
            case "PENDING" -> true;
            case "ALL" -> false;
            default -> throw new FieldValidationException("status", "Use PENDING or ALL.");
        };
    }
}
