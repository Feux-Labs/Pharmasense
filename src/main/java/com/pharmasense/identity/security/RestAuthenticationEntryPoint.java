package com.pharmasense.identity.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces Spring Security's default 401 (an HTML login page redirect) with
 * the same JSON {@code ApiResponse} envelope every other endpoint returns.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(ErrorCode.AUTHENTICATION_FAILED.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResponse.error(ErrorCode.AUTHENTICATION_FAILED.name(), "Authentication is required to access this resource")));
    }
}
