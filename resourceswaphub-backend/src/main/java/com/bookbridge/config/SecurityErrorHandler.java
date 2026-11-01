package com.bookbridge.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, 401, "UNAUTHORIZED", "Please sign in to continue.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, 403, "FORBIDDEN", "You do not have permission to perform this action.");
    }

    private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        String traceId = MDC.get("requestId");
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message
                + "\",\"details\":[],\"traceId\":\"" + traceId + "\"}}");
    }
}
