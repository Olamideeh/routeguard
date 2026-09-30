package com.example.routeguard.service;

import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvaluation;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryEvaluationServiceTest {

    @Mock
    private GpsDistanceService gpsDistanceService;

    @Mock
    private DeliveryEventRepository eventRepository;

    private DeliveryEvaluationService evaluationService;

    @BeforeEach
    void setUp() {
        evaluationService = new DeliveryEvaluationService(
                gpsDistanceService,
                eventRepository,
                new BigDecimal("200")
        );
    }

    @Test
    void deliveredEventWithValidEvidenceIsVerified() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        String photoHash = "a".repeat(64);

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .company(company)
                .eventType(DeliveryEventType.DELIVERED)
                .eventTimestamp(Instant.now())
                .deliveryLatitude(new BigDecimal("6.4541000"))
                .deliveryLongitude(new BigDecimal("3.3947000"))
                .expectedLatitude(new BigDecimal("6.4542000"))
                .expectedLongitude(new BigDecimal("3.3948000"))
                .otpVerified(true)
                .proofPhotoHash(photoHash)
                .customerContactAttempted(true)
                .build();

        when(gpsDistanceService.calculateDistanceMetres(
                event.getDeliveryLatitude(),
                event.getDeliveryLongitude(),
                event.getExpectedLatitude(),
                event.getExpectedLongitude()
        )).thenReturn(new BigDecimal("15.68"));

        when(eventRepository
                .existsByCompany_IdAndProofPhotoHashAndIdNot(
                        companyId,
                        photoHash,
                        eventId
                ))
                .thenReturn(false);

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertSame(
                        event,
                        evaluation.getDeliveryEvent()
                ),
                () -> assertEquals(
                        EvaluationDecision.VERIFIED,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.ALL_EVIDENCE_VERIFIED
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.NO_ACTION,
                        evaluation.getRecoveryAction()
                ),
                () -> assertEquals(
                        new BigDecimal("15.68"),
                        evaluation.getGpsDistanceMetres()
                ),
                () -> assertEquals(
                        Boolean.FALSE,
                        evaluation.getPhotoReused()
                ),
                () -> assertEquals(
                        0,
                        evaluation.getRiskScore()
                ),
                () -> assertNotNull(
                        evaluation.getDecisionExplanation()
                ),
                () -> assertFalse(
                        evaluation.getDecisionExplanation().isBlank()
                )
        );

        verify(eventRepository)
                .existsByCompany_IdAndProofPhotoHashAndIdNot(
                        companyId,
                        photoHash,
                        eventId
                );
    }
    @Test
    void failedEvidenceChecksProduceSuspiciousDecision() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        String photoHash = "b".repeat(64);

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .company(company)
                .eventType(DeliveryEventType.DELIVERED)
                .eventTimestamp(Instant.now())
                .deliveryLatitude(new BigDecimal("6.4541000"))
                .deliveryLongitude(new BigDecimal("3.3947000"))
                .expectedLatitude(new BigDecimal("6.4641000"))
                .expectedLongitude(new BigDecimal("3.4047000"))
                .otpVerified(false)
                .proofPhotoHash(photoHash)
                .build();

        when(gpsDistanceService.calculateDistanceMetres(
                event.getDeliveryLatitude(),
                event.getDeliveryLongitude(),
                event.getExpectedLatitude(),
                event.getExpectedLongitude()
        )).thenReturn(new BigDecimal("1500.00"));

        when(eventRepository
                .existsByCompany_IdAndProofPhotoHashAndIdNot(
                        companyId,
                        photoHash,
                        eventId
                ))
                .thenReturn(true);

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertEquals(
                        EvaluationDecision.SUSPICIOUS,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.GPS_DISTANCE_EXCEEDED,
                                DecisionReasonCode.OTP_INVALID,
                                DecisionReasonCode.PHOTO_REUSED
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.MANUAL_INVESTIGATION,
                        evaluation.getRecoveryAction()
                ),
                () -> assertEquals(
                        new BigDecimal("1500.00"),
                        evaluation.getGpsDistanceMetres()
                ),
                () -> assertEquals(
                        Boolean.TRUE,
                        evaluation.getPhotoReused()
                ),
                () -> assertEquals(
                        100,
                        evaluation.getRiskScore()
                )
        );
    }
    @Test
    void missingEvidenceRequiresReview() {
        DeliveryCompany company = new DeliveryCompany();
        company.setId(UUID.randomUUID());

        DeliveryEvent event = DeliveryEvent.builder()
                .id(UUID.randomUUID())
                .company(company)
                .eventType(DeliveryEventType.DELIVERED)
                .eventTimestamp(Instant.now())
                .build();

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertEquals(
                        EvaluationDecision.REVIEW_REQUIRED,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.GPS_EVIDENCE_MISSING,
                                DecisionReasonCode.OTP_EVIDENCE_MISSING,
                                DecisionReasonCode.PHOTO_EVIDENCE_MISSING
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.MANUAL_INVESTIGATION,
                        evaluation.getRecoveryAction()
                ),
                () -> assertNull(
                        evaluation.getGpsDistanceMetres()
                ),
                () -> assertNull(
                        evaluation.getPhotoReused()
                ),
                () -> assertEquals(
                        0,
                        evaluation.getRiskScore()
                )
        );

        verifyNoInteractions(
                gpsDistanceService,
                eventRepository
        );
    }
    @ParameterizedTest
    @CsvSource({
            "200.00, VERIFIED, 0",
            "200.01, SUSPICIOUS, 30"
    })
    void gpsDistanceBoundaryIsHandledCorrectly(
            String distance,
            EvaluationDecision expectedDecision,
            int expectedRiskScore
    ) {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        String photoHash = "c".repeat(64);

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .company(company)
                .eventType(DeliveryEventType.DELIVERED)
                .eventTimestamp(Instant.now())
                .deliveryLatitude(BigDecimal.ZERO)
                .deliveryLongitude(BigDecimal.ZERO)
                .expectedLatitude(BigDecimal.ZERO)
                .expectedLongitude(new BigDecimal("0.0018"))
                .otpVerified(true)
                .proofPhotoHash(photoHash)
                .build();

        when(gpsDistanceService.calculateDistanceMetres(
                event.getDeliveryLatitude(),
                event.getDeliveryLongitude(),
                event.getExpectedLatitude(),
                event.getExpectedLongitude()
        )).thenReturn(new BigDecimal(distance));

        when(eventRepository
                .existsByCompany_IdAndProofPhotoHashAndIdNot(
                        companyId,
                        photoHash,
                        eventId
                ))
                .thenReturn(false);

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        Set<DecisionReasonCode> expectedReasons =
                expectedDecision == EvaluationDecision.VERIFIED
                        ? Set.of(
                        DecisionReasonCode.ALL_EVIDENCE_VERIFIED
                )
                        : Set.of(
                        DecisionReasonCode.GPS_DISTANCE_EXCEEDED
                );

        assertAll(
                () -> assertEquals(
                        expectedDecision,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        expectedRiskScore,
                        evaluation.getRiskScore()
                ),
                () -> assertEquals(
                        expectedReasons,
                        evaluation.getReasonCodes()
                )
        );
    }
    @Test
    void failedCheckTakesPriorityOverMissingEvidence() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        String photoHash = "d".repeat(64);

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .company(company)
                .eventType(DeliveryEventType.DELIVERED)
                .eventTimestamp(Instant.now())
                .otpVerified(false)
                .proofPhotoHash(photoHash)
                .build();

        when(eventRepository
                .existsByCompany_IdAndProofPhotoHashAndIdNot(
                        companyId,
                        photoHash,
                        eventId
                ))
                .thenReturn(false);

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertEquals(
                        EvaluationDecision.SUSPICIOUS,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.GPS_EVIDENCE_MISSING,
                                DecisionReasonCode.OTP_INVALID
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        40,
                        evaluation.getRiskScore()
                ),
                () -> assertEquals(
                        RecoveryAction.MANUAL_INVESTIGATION,
                        evaluation.getRecoveryAction()
                ),
                () -> assertNull(
                        evaluation.getGpsDistanceMetres()
                ),
                () -> assertEquals(
                        Boolean.FALSE,
                        evaluation.getPhotoReused()
                )
        );

        verifyNoInteractions(gpsDistanceService);
    }
    @Test
    void failedDeliveryWithoutContactAttemptRequiresReview() {
        DeliveryCompany company = new DeliveryCompany();
        company.setId(UUID.randomUUID());

        DeliveryEvent event = DeliveryEvent.builder()
                .id(UUID.randomUUID())
                .company(company)
                .eventType(DeliveryEventType.DELIVERY_FAILED)
                .eventTimestamp(Instant.now())
                .failureReason("Customer unavailable")
                .customerContactAttempted(false)
                .build();

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertEquals(
                        EvaluationDecision.REVIEW_REQUIRED,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.CONTACT_ATTEMPT_MISSING
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.CONTACT_CUSTOMER,
                        evaluation.getRecoveryAction()
                ),
                () -> assertEquals(
                        0,
                        evaluation.getRiskScore()
                ),
                () -> assertNotNull(
                        evaluation.getDecisionExplanation()
                ),
                () -> assertFalse(
                        evaluation.getDecisionExplanation().isBlank()
                )
        );

        verifyNoInteractions(
                gpsDistanceService,
                eventRepository
        );
    }
    @Test
    void failedDeliveryWithReasonAndContactStillRequiresReview() {
        DeliveryCompany company = new DeliveryCompany();
        company.setId(UUID.randomUUID());

        DeliveryEvent event = DeliveryEvent.builder()
                .id(UUID.randomUUID())
                .company(company)
                .eventType(DeliveryEventType.DELIVERY_FAILED)
                .eventTimestamp(Instant.now())
                .failureReason("Customer unavailable")
                .customerContactAttempted(true)
                .build();

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertEquals(
                        EvaluationDecision.REVIEW_REQUIRED,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.INSUFFICIENT_EVIDENCE
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.MANUAL_INVESTIGATION,
                        evaluation.getRecoveryAction()
                ),
                () -> assertEquals(
                        0,
                        evaluation.getRiskScore()
                ),
                () -> assertNull(
                        evaluation.getGpsDistanceMetres()
                ),
                () -> assertNull(
                        evaluation.getPhotoReused()
                )
        );

        verifyNoInteractions(
                gpsDistanceService,
                eventRepository
        );
    }
    @Test
    void failedDeliveryWithMissingDetailsRequiresInvestigation() {
        DeliveryCompany company = new DeliveryCompany();
        company.setId(UUID.randomUUID());

        DeliveryEvent event = DeliveryEvent.builder()
                .id(UUID.randomUUID())
                .company(company)
                .eventType(DeliveryEventType.DELIVERY_FAILED)
                .eventTimestamp(Instant.now())
                .failureReason("   ")
                .customerContactAttempted(null)
                .build();

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        assertAll(
                () -> assertEquals(
                        EvaluationDecision.REVIEW_REQUIRED,
                        evaluation.getDecision()
                ),
                () -> assertEquals(
                        Set.of(
                                DecisionReasonCode.FAILURE_REASON_MISSING,
                                DecisionReasonCode.CONTACT_ATTEMPT_MISSING
                        ),
                        evaluation.getReasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.MANUAL_INVESTIGATION,
                        evaluation.getRecoveryAction()
                ),
                () -> assertEquals(
                        0,
                        evaluation.getRiskScore()
                )
        );

        verifyNoInteractions(
                gpsDistanceService,
                eventRepository
        );
    }
}