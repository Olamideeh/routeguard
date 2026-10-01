package com.example.routeguard.dto;

import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.enums.ReviewDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RiskReviewRequest(

        @NotNull(message = "Review decision is required")
        ReviewDecision decision,

        @NotNull(message = "Recovery action is required")
        RecoveryAction recoveryAction,

        @NotBlank(message = "Review notes are required")
        @Size(
                max = 2000,
                message = "Review notes cannot exceed 2000 characters"
        )
        String notes
) {
}