package com.example.routeguard.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BootstrapPlatformAdminRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email address is invalid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(
                min = 12,
                max = 72,
                message = "Platform administrator password must contain between 12 and 72 characters"
        )
        String password
) {
}