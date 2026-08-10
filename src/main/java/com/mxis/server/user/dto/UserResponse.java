package com.mxis.server.user.dto;

import com.mxis.server.common.enums.AuthProvider;
import com.mxis.server.user.entity.User;
import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String name,
        String phone,
        AuthProvider provider,
        LocalDateTime createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getProvider(),
                user.getCreatedAt());
    }
}
