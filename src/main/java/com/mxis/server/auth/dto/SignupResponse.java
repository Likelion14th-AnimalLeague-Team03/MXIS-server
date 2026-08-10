package com.mxis.server.auth.dto;

public record SignupResponse(
        Long userId,
        String email,
        String name
) {
}
