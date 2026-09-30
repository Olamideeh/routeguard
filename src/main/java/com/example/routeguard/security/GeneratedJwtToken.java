package com.example.routeguard.security;

import java.time.Instant;

public record GeneratedJwtToken(
        String value,
        Instant expiresAt
) {
}