package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEvaluationResponse;
import com.example.routeguard.entity.DeliveryEvaluation;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.exception.BusinessRuleException;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.repository.DeliveryEvaluationRepository;
import com.example.routeguard.repository.DeliveryEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryEventProcessingService {

    private final DeliveryEventRepository eventRepository;
    private final DeliveryEvaluationRepository evaluationRepository;
    private final DeliveryEvaluationService evaluationService;

    @Transactional
    public DeliveryEvaluationResponse processEvent(
            UUID companyId,
            UUID eventId
    ) {
        DeliveryEvent event = eventRepository
                .findByIdAndCompany_Id(eventId, companyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Delivery event not found with ID: "
                                        + eventId
                        )
                );

        // Return an existing evaluation without evaluating again.
        Optional<DeliveryEvaluation> existingEvaluation =
                evaluationRepository.findByDeliveryEvent_Id(eventId);

        if (existingEvaluation.isPresent()) {
            return mapToResponse(existingEvaluation.get());
        }

        if (event.getStatus() != EvaluationStatus.RECEIVED) {
            throw new BusinessRuleException(
                    "Only events with RECEIVED status "
                            + "can be evaluated"
            );
        }

        event.setStatus(EvaluationStatus.EVALUATING);

        DeliveryEvaluation evaluation =
                evaluationService.evaluate(event);

        DeliveryEvaluation savedEvaluation =
                evaluationRepository.save(evaluation);

        EvaluationStatus finalStatus =
                switch (savedEvaluation.getDecision()) {
                    case VERIFIED -> EvaluationStatus.VERIFIED;
                    case SUSPICIOUS -> EvaluationStatus.SUSPICIOUS;
                    case REVIEW_REQUIRED ->
                            EvaluationStatus.REVIEW_REQUIRED;
                };

        event.setStatus(finalStatus);
        eventRepository.save(event);

        return mapToResponse(savedEvaluation);
    }

    private DeliveryEvaluationResponse mapToResponse(
            DeliveryEvaluation evaluation
    ) {
        DeliveryEvent event = evaluation.getDeliveryEvent();

        return new DeliveryEvaluationResponse(
                evaluation.getId(),
                event.getId(),
                event.getReference(),
                evaluation.getDecision(),
                evaluation.getReasonCodes(),
                evaluation.getRecoveryAction(),
                evaluation.getGpsDistanceMetres(),
                evaluation.getPhotoReused(),
                evaluation.getRiskScore(),
                evaluation.getDecisionExplanation(),
                evaluation.getEvaluatedAt()
        );
    }
}