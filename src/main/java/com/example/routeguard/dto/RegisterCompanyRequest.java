package com.example.routeguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterCompanyRequest(

        @NotBlank(message = "Company name is required")
        @Size(max = 150, message = "Company name cannot exceed 150 characters")
        String name,

        @NotBlank(message = "Company code is required")
        @Pattern(
                regexp = "^[A-Za-z0-9_-]+$",
                message = "Company code can contain only letters, numbers, underscores and hyphens"
        )
        @Size(max = 50, message = "Company code cannot exceed 50 characters")
        String companyCode,

        @NotBlank(message = "Country code is required")
        @Pattern(
                regexp = "^[A-Za-z]{2}$",
                message = "Country code must contain exactly two letters"
        )
        String countryCode
) {
}