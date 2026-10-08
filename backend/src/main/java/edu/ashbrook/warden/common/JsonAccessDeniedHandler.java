package edu.ashbrook.warden.common;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper json;

    public JsonAccessDeniedHandler(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        JsonAuthEntryPoint.write(json, response,
                ApiError.of(403, ErrorCode.FORBIDDEN, "You don't have permission to do that."));
    }
}
