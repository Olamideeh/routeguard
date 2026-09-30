package com.example.routeguard.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID userId,
        String email,
        String role,
        UUID companyId
) {
}