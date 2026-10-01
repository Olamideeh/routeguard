package com.example.routeguard.controller;

import com.example.routeguard.config.SecurityConfig;
import com.example.routeguard.dto.RiskReviewRequest;
import com.example.routeguard.dto.RiskReviewResponse;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.enums.ReviewDecision;
import com.example.routeguard.service.RiskReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RiskReviewController.class)
@Import(SecurityConfig.class)
class RiskReviewControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RiskReviewService reviewService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String VALID_REVIEW_JSON = """
            {
              "decision": "INCONCLUSIVE",
              "recoveryAction": "MANUAL_INVESTIGATION",
              "notes": "Additional evidence is required."
            }
            """;

    @Test
    void reviewWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVIEW_JSON)
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService, jwtDecoder);
    }

    @Test
    void historyWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                get(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService, jwtDecoder);
    }

    @Test
    void rejectedTokenReturnsUnauthorized() throws Exception {
        when(jwtDecoder.decode("invalid-token"))
                .thenThrow(new BadJwtException("Invalid token"));

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
                        .header(
                                "Authorization",
                                "Bearer invalid-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVIEW_JSON)
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "COMPANY_ADMIN",
            "OPERATIONS_OFFICER",
            "PLATFORM_ADMIN"
    })
    void otherRolesCannotSubmitReview(String role) throws Exception {
        authenticate(
                role,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVIEW_JSON)
        ).andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }

    @Test
    void riskReviewerCanSubmitReviewUsingJwtIdentity() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        authenticate("RISK_REVIEWER", companyId, reviewerId);

        RiskReviewRequest expectedRequest = new RiskReviewRequest(
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.MANUAL_INVESTIGATION,
                "Additional evidence is required."
        );

        RiskReviewResponse response = new RiskReviewResponse(
                reviewId,
                eventId,
                "EVT-REVIEW-HTTP-001",
                reviewerId,
                "Daniel Okafor",
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.MANUAL_INVESTIGATION,
                expectedRequest.notes(),
                Instant.parse("2026-09-30T20:01:00Z"),
                EvaluationStatus.UNDER_REVIEW
        );

        when(reviewService.submitReview(
                companyId,
                eventId,
                reviewerId,
                expectedRequest
        )).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/v1/delivery-events/{eventId}/reviews",
                                eventId
                        )
                                .header("Authorization", "Bearer test-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_REVIEW_JSON)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id")
                        .value(reviewId.toString()))
                .andExpect(jsonPath("$.deliveryEventId")
                        .value(eventId.toString()))
                .andExpect(jsonPath("$.reviewerId")
                        .value(reviewerId.toString()))
                .andExpect(jsonPath("$.decision")
                        .value("INCONCLUSIVE"))
                .andExpect(jsonPath("$.eventStatus")
                        .value("UNDER_REVIEW"));

        verify(reviewService).submitReview(
                companyId,
                eventId,
                reviewerId,
                expectedRequest
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            """
            {
              "recoveryAction": "MANUAL_INVESTIGATION",
              "notes": "Additional evidence is required."
            }
            """,
            """
            {
              "decision": "INCONCLUSIVE",
              "notes": "Additional evidence is required."
            }
            """,
            """
            {
              "decision": "INCONCLUSIVE",
              "recoveryAction": "MANUAL_INVESTIGATION",
              "notes": "   "
            }
            """
    })
    void missingRequiredReviewFieldsReturnBadRequest(
            String body
    ) throws Exception {
        authenticate(
                "RISK_REVIEWER",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void oversizedNotesReturnBadRequest() throws Exception {
        authenticate(
                "RISK_REVIEWER",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        String body = """
                {
                  "decision": "INCONCLUSIVE",
                  "recoveryAction": "MANUAL_INVESTIGATION",
                  "notes": "%s"
                }
                """.formatted("a".repeat(2001));

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void invalidDecisionReturnsBadRequest() throws Exception {
        authenticate(
                "RISK_REVIEWER",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                )
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "UNKNOWN",
                                  "recoveryAction": "MANUAL_INVESTIGATION",
                                  "notes": "Additional evidence is required."
                                }
                                """)
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @Test
    void malformedEventIdReturnsBadRequest() throws Exception {
        authenticate(
                "RISK_REVIEWER",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(
                post("/api/v1/delivery-events/not-a-uuid/reviews")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REVIEW_JSON)
        ).andExpect(status().isBadRequest());

        verifyNoInteractions(reviewService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "COMPANY_ADMIN",
            "OPERATIONS_OFFICER",
            "RISK_REVIEWER"
    })
    void companyRolesCanRetrieveHistory(String role) throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        authenticate(role, companyId, reviewerId);

        RiskReviewResponse review = new RiskReviewResponse(
                reviewId,
                eventId,
                "EVT-HISTORY-HTTP-001",
                reviewerId,
                "Daniel Okafor",
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.MANUAL_INVESTIGATION,
                "Additional evidence is required.",
                Instant.parse("2026-09-30T20:01:00Z"),
                EvaluationStatus.UNDER_REVIEW
        );

        when(reviewService.getReviewHistory(companyId, eventId))
                .thenReturn(List.of(review));

        mockMvc.perform(
                        get(
                                "/api/v1/delivery-events/{eventId}/reviews",
                                eventId
                        ).header("Authorization", "Bearer test-token")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id")
                        .value(reviewId.toString()))
                .andExpect(jsonPath("$[0].deliveryEventId")
                        .value(eventId.toString()));

        verify(reviewService).getReviewHistory(companyId, eventId);
    }

    @Test
    void emptyHistoryReturnsEmptyArray() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        authenticate(
                "RISK_REVIEWER",
                companyId,
                UUID.randomUUID()
        );

        when(reviewService.getReviewHistory(companyId, eventId))
                .thenReturn(List.of());

        mockMvc.perform(
                        get(
                                "/api/v1/delivery-events/{eventId}/reviews",
                                eventId
                        ).header("Authorization", "Bearer test-token")
                )
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(reviewService).getReviewHistory(companyId, eventId);
    }

    @Test
    void platformAdminCannotRetrieveCompanyReviewHistory()
            throws Exception {
        authenticate(
                "PLATFORM_ADMIN",
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        mockMvc.perform(
                get(
                        "/api/v1/delivery-events/{eventId}/reviews",
                        UUID.randomUUID()
                ).header("Authorization", "Bearer test-token")
        ).andExpect(status().isForbidden());

        verifyNoInteractions(reviewService);
    }

    private void authenticate(
            String role,
            UUID companyId,
            UUID userId
    ) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("companyId", companyId.toString())
                .claim("role", role)
                .build();

        when(jwtDecoder.decode("test-token"))
                .thenReturn(jwt);
    }
}