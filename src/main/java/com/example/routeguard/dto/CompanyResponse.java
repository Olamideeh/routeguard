package com.example.routeguard.dto;

import com.example.routeguard.enums.CompanyStatus;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String name,
        String companyCode,
        String countryCode,
        CompanyStatus status,
        Instant createdAt
) {
}