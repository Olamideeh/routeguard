package com.example.routeguard.controller;

import com.example.routeguard.dto.RiskReviewRequest;
import com.example.routeguard.dto.RiskReviewResponse;
import com.example.routeguard.service.RiskReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery-events")
@RequiredArgsConstructor
public class RiskReviewController {

    private final RiskReviewService reviewService;

    @PostMapping("/{eventId}/reviews")
    @PreAuthorize("hasRole('RISK_REVIEWER')")
    public ResponseEntity<RiskReviewResponse> submitReview(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RiskReviewRequest request
    ) {
        UUID companyId = UUID.fromString(
                jwt.getClaimAsString("companyId")
        );

        UUID reviewerId = UUID.fromString(
                jwt.getSubject()
        );

        RiskReviewResponse response =
                reviewService.submitReview(
                        companyId,
                        eventId,
                        reviewerId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @GetMapping("/{eventId}/reviews")
    @PreAuthorize(
            "hasAnyRole('COMPANY_ADMIN', 'OPERATIONS_OFFICER', 'RISK_REVIEWER')"
    )
    public ResponseEntity<List<RiskReviewResponse>> getReviewHistory(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID companyId = UUID.fromString(
                jwt.getClaimAsString("companyId")
        );

        List<RiskReviewResponse> response =
                reviewService.getReviewHistory(
                        companyId,
                        eventId
                );

        return ResponseEntity.ok(response);
    }
}