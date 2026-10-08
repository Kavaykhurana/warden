package edu.ashbrook.warden.common;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint {

    private final JsonMapper json;

    public JsonAuthEntryPoint(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(json, response, ApiError.of(401, ErrorCode.UNAUTHORIZED, "Sign in to continue."));
    }

    static void write(JsonMapper json, HttpServletResponse response, ApiError error) throws IOException {
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), error);
    }
}
