package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEvaluationResponse;
import com.example.routeguard.entity.DeliveryEvaluation;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.repository.DeliveryEvaluationRepository;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryEventProcessingServiceTest {

    @Mock
    private DeliveryEventRepository eventRepository;

    @Mock
    private DeliveryEvaluationRepository evaluationRepository;

    @Mock
    private DeliveryEvaluationService evaluationService;

    private DeliveryEventProcessingService processingService;

    @BeforeEach
    void setUp() {
        processingService = new DeliveryEventProcessingService(
                eventRepository,
                evaluationRepository,
                evaluationService
        );
    }

    @Test
    void processingSavesEvaluationAndUpdatesEventStatus() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();

        Instant evaluatedAt =
                Instant.parse("2026-09-30T20:01:00Z");

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-PROCESSING-001")
                .status(EvaluationStatus.RECEIVED)
                .build();

        DeliveryEvaluation evaluation =
                DeliveryEvaluation.builder()
                        .deliveryEvent(event)
                        .decision(EvaluationDecision.VERIFIED)
                        .reasonCodes(
                                Set.of(
                                        DecisionReasonCode.ALL_EVIDENCE_VERIFIED
                                )
                        )
                        .recoveryAction(RecoveryAction.NO_ACTION)
                        .gpsDistanceMetres(new BigDecimal("15.68"))
                        .photoReused(false)
                        .riskScore(0)
                        .decisionExplanation("All evidence checks passed.")
                        .build();

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.of(event));

        when(evaluationRepository.findByDeliveryEvent_Id(eventId))
                .thenReturn(Optional.empty());

        when(evaluationService.evaluate(event))
                .thenReturn(evaluation);

        // Simulate values assigned when the evaluation is persisted.
        when(evaluationRepository.save(evaluation))
                .thenAnswer(invocation -> {
                    DeliveryEvaluation saved =
                            invocation.getArgument(0);

                    saved.setId(evaluationId);
                    saved.setEvaluatedAt(evaluatedAt);

                    return saved;
                });

        DeliveryEvaluationResponse response =
                processingService.processEvent(
                        companyId,
                        eventId
                );

        assertAll(
                () -> assertEquals(
                        EvaluationStatus.VERIFIED,
                        event.getStatus()
                ),
                () -> assertEquals(
                        evaluationId,
                        response.id()
                ),
                () -> assertEquals(
                        eventId,
                        response.deliveryEventId()
                ),
                () -> assertEquals(
                        "EVT-PROCESSING-001",
                        response.eventReference()
                ),
                () -> assertEquals(
                        EvaluationDecision.VERIFIED,
                        response.decision()
                ),
                () -> assertEquals(
                        evaluation.getReasonCodes(),
                        response.reasonCodes()
                ),
                () -> assertEquals(
                        RecoveryAction.NO_ACTION,
                        response.recoveryAction()
                ),
                () -> assertEquals(
                        new BigDecimal("15.68"),
                        response.gpsDistanceMetres()
                ),
                () -> assertEquals(
                        evaluatedAt,
                        response.evaluatedAt()
                )
        );

        verify(evaluationRepository).save(evaluation);
        verify(eventRepository).save(event);
    }
    @Test
    void eventOutsideCompanyCannotBeProcessed() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> processingService.processEvent(
                        companyId,
                        eventId
                )
        );

        assertEquals(
                "Delivery event not found with ID: " + eventId,
                exception.getMessage()
        );

        verifyNoInteractions(
                evaluationRepository,
                evaluationService
        );

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @Test
    void alreadyEvaluatedEventReturnsExistingResult() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();

        Instant evaluatedAt =
                Instant.parse("2026-09-30T20:01:00Z");

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-EXISTING-001")
                .status(EvaluationStatus.SUSPICIOUS)
                .build();

        DeliveryEvaluation existingEvaluation =
                DeliveryEvaluation.builder()
                        .id(evaluationId)
                        .deliveryEvent(event)
                        .decision(EvaluationDecision.SUSPICIOUS)
                        .reasonCodes(
                                Set.of(DecisionReasonCode.OTP_INVALID)
                        )
                        .recoveryAction(
                                RecoveryAction.MANUAL_INVESTIGATION
                        )
                        .riskScore(40)
                        .decisionExplanation("OTP verification failed.")
                        .evaluatedAt(evaluatedAt)
                        .build();

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.of(event));

        when(evaluationRepository.findByDeliveryEvent_Id(eventId))
                .thenReturn(Optional.of(existingEvaluation));

        DeliveryEvaluationResponse response =
                processingService.processEvent(
                        companyId,
                        eventId
                );

        assertAll(
                () -> assertEquals(evaluationId, response.id()),
                () -> assertEquals(eventId, response.deliveryEventId()),
                () -> assertEquals(
                        EvaluationDecision.SUSPICIOUS,
                        response.decision()
                ),
                () -> assertEquals(
                        Set.of(DecisionReasonCode.OTP_INVALID),
                        response.reasonCodes()
                ),
                () -> assertEquals(evaluatedAt, response.evaluatedAt()),
                () -> assertEquals(
                        EvaluationStatus.SUSPICIOUS,
                        event.getStatus()
                )
        );

        verifyNoInteractions(evaluationService);

        verify(evaluationRepository, never())
                .save(any(DeliveryEvaluation.class));

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @Test
    void eventWithoutEvaluationMustHaveReceivedStatus() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .status(EvaluationStatus.RESOLVED)
                .build();

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.of(event));

        when(evaluationRepository.findByDeliveryEvent_Id(eventId))
                .thenReturn(Optional.empty());

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> processingService.processEvent(
                        companyId,
                        eventId
                )
        );

        assertEquals(
                "Only events with RECEIVED status can be evaluated",
                exception.getMessage()
        );

        assertEquals(
                EvaluationStatus.RESOLVED,
                event.getStatus()
        );

        verifyNoInteractions(evaluationService);

        verify(evaluationRepository, never())
                .save(any(DeliveryEvaluation.class));

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @Test
    void evaluationOutsideCompanyCannotBeRetrieved() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(evaluationRepository
                .findByDeliveryEvent_IdAndDeliveryEvent_Company_Id(
                        eventId,
                        companyId
                ))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> processingService.getEvaluation(
                        companyId,
                        eventId
                )
        );

        assertEquals(
                "Evaluation not found for delivery event: " + eventId,
                exception.getMessage()
        );

        verifyNoInteractions(
                eventRepository,
                evaluationService
        );

        verify(evaluationRepository, never())
                .save(any(DeliveryEvaluation.class));
    }
    @Test
    void retrievalReturnsSavedEvaluationWithoutProcessing() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID evaluationId = UUID.randomUUID();

        Instant evaluatedAt =
                Instant.parse("2026-09-30T20:01:00Z");

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-RETRIEVAL-001")
                .status(EvaluationStatus.REVIEW_REQUIRED)
                .build();

        DeliveryEvaluation evaluation = DeliveryEvaluation.builder()
                .id(evaluationId)
                .deliveryEvent(event)
                .decision(EvaluationDecision.REVIEW_REQUIRED)
                .reasonCodes(
                        Set.of(DecisionReasonCode.GPS_EVIDENCE_MISSING)
                )
                .recoveryAction(RecoveryAction.MANUAL_INVESTIGATION)
                .riskScore(0)
                .decisionExplanation("GPS evidence is missing.")
                .evaluatedAt(evaluatedAt)
                .build();

        when(evaluationRepository
                .findByDeliveryEvent_IdAndDeliveryEvent_Company_Id(
                        eventId,
                        companyId
                ))
                .thenReturn(Optional.of(evaluation));

        DeliveryEvaluationResponse response =
                processingService.getEvaluation(companyId, eventId);

        assertAll(
                () -> assertEquals(evaluationId, response.id()),
                () -> assertEquals(eventId, response.deliveryEventId()),
                () -> assertEquals(
                        "EVT-RETRIEVAL-001",
                        response.eventReference()
                ),
                () -> assertEquals(
                        EvaluationDecision.REVIEW_REQUIRED,
                        response.decision()
                ),
                () -> assertEquals(
                        evaluation.getReasonCodes(),
                        response.reasonCodes()
                ),
                () -> assertEquals(evaluatedAt, response.evaluatedAt()),
                () -> assertEquals(
                        EvaluationStatus.REVIEW_REQUIRED,
                        event.getStatus()
                )
        );

        verifyNoInteractions(eventRepository, evaluationService);

        verify(evaluationRepository, never())
                .save(any(DeliveryEvaluation.class));
    }
}