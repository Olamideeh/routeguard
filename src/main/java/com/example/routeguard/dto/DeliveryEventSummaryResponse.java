package com.example.routeguard.dto;

import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationStatus;

import java.time.Instant;
import java.util.UUID;

public record DeliveryEventSummaryResponse(
        UUID id,
        String reference,
        String externalDeliveryId,
        DeliveryEventType eventType,
        EvaluationStatus status,
        Instant eventTimestamp,
        Instant createdAt
) {
}