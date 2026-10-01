package com.example.routeguard.controller;

import com.example.routeguard.dto.DeliveryEvaluationResponse;
import com.example.routeguard.service.DeliveryEventProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery-events")
@RequiredArgsConstructor
public class DeliveryEventController {

    private final DeliveryEventProcessingService processingService;

    @PostMapping("/{eventId}/evaluate")
    @PreAuthorize("hasRole('OPERATIONS_OFFICER')")
    public ResponseEntity<DeliveryEvaluationResponse> evaluateEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID companyId = UUID.fromString(
                jwt.getClaimAsString("companyId")
        );

        DeliveryEvaluationResponse response =
                processingService.processEvent(
                        companyId,
                        eventId
                );

        return ResponseEntity.ok(response);
    }
}