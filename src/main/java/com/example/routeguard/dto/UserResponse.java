package com.example.routeguard.dto;

import com.example.routeguard.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        UserRole role,
        UUID companyId,
        boolean active,
        Instant createdAt
) {
}