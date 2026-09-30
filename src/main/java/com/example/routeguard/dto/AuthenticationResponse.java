package com.example.routeguard.dto;

import com.example.routeguard.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UUID userId,
        String email,
        UserRole role,
        UUID companyId
) {
}