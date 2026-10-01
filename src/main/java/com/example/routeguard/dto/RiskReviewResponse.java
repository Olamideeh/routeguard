package com.example.routeguard.dto;

import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.enums.ReviewDecision;

import java.time.Instant;
import java.util.UUID;

public record RiskReviewResponse(
        UUID id,
        UUID deliveryEventId,
        String eventReference,
        UUID reviewerId,
        String reviewerName,
        ReviewDecision decision,
        RecoveryAction recoveryAction,
        String notes,
        Instant reviewedAt,
        EvaluationStatus eventStatus
) {
}