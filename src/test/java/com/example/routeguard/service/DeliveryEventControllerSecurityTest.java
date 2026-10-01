package com.example.routeguard.service;

import com.example.routeguard.config.SecurityConfig;
import com.example.routeguard.controller.DeliveryEventController;
import com.example.routeguard.service.DeliveryEventProcessingService;
import com.example.routeguard.service.DeliveryEventQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import com.example.routeguard.dto.DeliveryEvaluationResponse;
import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.RecoveryAction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.Instant;
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.mockito.Mockito.when;

import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeliveryEventController.class)
@Import(SecurityConfig.class)
class DeliveryEventControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeliveryEventProcessingService processingService;

    @MockitoBean
    private DeliveryEventQueryService queryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void evaluationWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/evaluate",
                        UUID.randomUUID()
                )
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(
                processingService,
                queryService,
                jwtDecoder
        );
    }
    @Test
    void companyAdministratorCannotEvaluateEvent() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("company-admin-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("companyId", companyId.toString())
                .claim("role", "COMPANY_ADMIN")
                .build();

        when(jwtDecoder.decode("company-admin-token"))
                .thenReturn(jwt);

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/evaluate",
                        UUID.randomUUID()
                ).header(
                        "Authorization",
                        "Bearer company-admin-token"
                )
        ).andExpect(status().isForbidden());

        verifyNoInteractions(
                processingService,
                queryService
        );
    }
    @Test
    void operationsOfficerCanEvaluateEvent() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("operations-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("companyId", companyId.toString())
                .claim("role", "OPERATIONS_OFFICER")
                .build();

        when(jwtDecoder.decode("operations-token"))
                .thenReturn(jwt);

        DeliveryEvaluationResponse response =
                new DeliveryEvaluationResponse(
                        evaluationId,
                        eventId,
                        "EVT-HTTP-001",
                        EvaluationDecision.VERIFIED,
                        Set.of(
                                DecisionReasonCode.ALL_EVIDENCE_VERIFIED
                        ),
                        RecoveryAction.NO_ACTION,
                        null,
                        false,
                        0,
                        "All evidence checks passed.",
                        Instant.parse("2026-09-30T20:01:00Z")
                );

        when(processingService.processEvent(companyId, eventId))
                .thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/v1/delivery-events/{eventId}/evaluate",
                                eventId
                        ).header(
                                "Authorization",
                                "Bearer operations-token"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(evaluationId.toString()))
                .andExpect(jsonPath("$.deliveryEventId")
                        .value(eventId.toString()))
                .andExpect(jsonPath("$.decision")
                        .value("VERIFIED"));

        verify(processingService).processEvent(companyId, eventId);
        verifyNoInteractions(queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "COMPANY_ADMIN",
            "OPERATIONS_OFFICER",
            "RISK_REVIEWER"
    })
    void companyRolesCanRetrieveEvaluation(String role) throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("read-token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("companyId", companyId.toString())
                .claim("role", role)
                .build();

        when(jwtDecoder.decode("read-token"))
                .thenReturn(jwt);

        DeliveryEvaluationResponse response =
                new DeliveryEvaluationResponse(
                        evaluationId,
                        eventId,
                        "EVT-READ-001",
                        EvaluationDecision.REVIEW_REQUIRED,
                        Set.of(
                                DecisionReasonCode.INSUFFICIENT_EVIDENCE
                        ),
                        RecoveryAction.MANUAL_INVESTIGATION,
                        null,
                        null,
                        0,
                        "Additional evidence is required.",
                        Instant.parse("2026-09-30T20:01:00Z")
                );

        when(processingService.getEvaluation(companyId, eventId))
                .thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/v1/delivery-events/{eventId}/evaluation",
                                eventId
                        ).header(
                                "Authorization",
                                "Bearer read-token"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(evaluationId.toString()))
                .andExpect(jsonPath("$.decision")
                        .value("REVIEW_REQUIRED"));

        verify(processingService).getEvaluation(companyId, eventId);
        verifyNoInteractions(queryService);
    }
    @Test
    void riskReviewerCannotEvaluateEvent() throws Exception {
        Jwt jwt = Jwt.withTokenValue("risk-token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .claim("companyId", UUID.randomUUID().toString())
                .claim("role", "RISK_REVIEWER")
                .build();

        when(jwtDecoder.decode("risk-token"))
                .thenReturn(jwt);

        mockMvc.perform(
                post(
                        "/api/v1/delivery-events/{eventId}/evaluate",
                        UUID.randomUUID()
                ).header(
                        "Authorization",
                        "Bearer risk-token"
                )
        ).andExpect(status().isForbidden());

        verifyNoInteractions(processingService, queryService);
    }
}