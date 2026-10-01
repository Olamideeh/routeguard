package com.example.routeguard.dto;

import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.RecoveryAction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DeliveryEvaluationResponse(
        UUID id,
        UUID deliveryEventId,
        String eventReference,
        EvaluationDecision decision,
        Set<DecisionReasonCode> reasonCodes,
        RecoveryAction recoveryAction,
        BigDecimal gpsDistanceMetres,
        Boolean photoReused,
        int riskScore,
        String decisionExplanation,
        Instant evaluatedAt
) {
    public DeliveryEvaluationResponse {
        reasonCodes = Set.copyOf(reasonCodes);
    }
}