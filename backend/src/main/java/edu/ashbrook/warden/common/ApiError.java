package edu.ashbrook.warden.common;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ApiError(
        int status,
        ErrorCode code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fieldErrors) {

    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(status, code, message, null);
    }
}
