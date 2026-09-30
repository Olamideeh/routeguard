package com.example.routeguard.dto;

import java.time.Instant;
import java.util.UUID;

public record ApiCredentialResponse(
        UUID credentialId,
        UUID keyId,
        String apiKey,
        Instant createdAt,
        Instant expiresAt
) {
}