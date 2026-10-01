package com.example.routeguard.service;

import com.example.routeguard.dto.RiskReviewRequest;
import com.example.routeguard.dto.RiskReviewResponse;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.entity.PlatformUser;
import com.example.routeguard.entity.RiskReview;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.enums.ReviewDecision;
import com.example.routeguard.enums.UserRole;
import com.example.routeguard.exception.BusinessRuleException;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.repository.DeliveryEventRepository;
import com.example.routeguard.repository.PlatformUserRepository;
import com.example.routeguard.repository.RiskReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import com.example.routeguard.entity.DeliveryCompany;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskReviewService {

    private final DeliveryEventRepository eventRepository;
    private final PlatformUserRepository userRepository;
    private final RiskReviewRepository reviewRepository;

    @Transactional
    public RiskReviewResponse submitReview(
            UUID companyId,
            UUID eventId,
            UUID reviewerId,
            RiskReviewRequest request
    ) {
        PlatformUser reviewer = userRepository
                .findById(reviewerId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "Reviewer is not authorized"
                        )
                );

        validateReviewer(reviewer, companyId);
        validateRequest(request);

        // This repository method already has PESSIMISTIC_WRITE.
        DeliveryEvent event = eventRepository
                .findByIdAndCompany_Id(eventId, companyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Delivery event not found with ID: "
                                        + eventId
                        )
                );

        validateEventStatus(event);

        RiskReview review = RiskReview.builder()
                .deliveryEvent(event)
                .reviewer(reviewer)
                .decision(request.decision())
                .recoveryAction(request.recoveryAction())
                .notes(request.notes().trim())
                .build();

        RiskReview savedReview =
                reviewRepository.save(review);

        EvaluationStatus nextStatus =
                request.decision() == ReviewDecision.INCONCLUSIVE
                        ? EvaluationStatus.UNDER_REVIEW
                        : EvaluationStatus.RESOLVED;

        event.setStatus(nextStatus);
        eventRepository.save(event);

        return mapToResponse(savedReview);
    }

    private void validateReviewer(
            PlatformUser reviewer,
            UUID companyId
    ) {
        if (companyId == null
                || !reviewer.isActive()
                || reviewer.getRole() != UserRole.RISK_REVIEWER
                || reviewer.getCompany() == null
                || !companyId.equals(
                reviewer.getCompany().getId()
        )
                || reviewer.getCompany().getStatus()
                != CompanyStatus.ACTIVE) {
            throw new AccessDeniedException(
                    "Reviewer is not authorized"
            );
        }
    }

    private void validateRequest(
            RiskReviewRequest request
    ) {
        if (request == null
                || request.decision() == null
                || request.recoveryAction() == null
                || request.notes() == null
                || request.notes().isBlank()) {
            throw new BusinessRuleException(
                    "Review decision, recovery action, "
                            + "and notes are required"
            );
        }

        if (request.notes().length() > 2000) {
            throw new BusinessRuleException(
                    "Review notes cannot exceed 2000 characters"
            );
        }

        if (request.decision() == ReviewDecision.INCONCLUSIVE
                && request.recoveryAction()
                != RecoveryAction.MANUAL_INVESTIGATION) {
            throw new BusinessRuleException(
                    "An inconclusive review requires "
                            + "MANUAL_INVESTIGATION"
            );
        }
    }

    private void validateEventStatus(
            DeliveryEvent event
    ) {
        EvaluationStatus status = event.getStatus();

        if (status != EvaluationStatus.SUSPICIOUS
                && status != EvaluationStatus.REVIEW_REQUIRED
                && status != EvaluationStatus.UNDER_REVIEW) {
            throw new BusinessRuleException(
                    "Only SUSPICIOUS, REVIEW_REQUIRED, "
                            + "or UNDER_REVIEW events can be reviewed"
            );
        }
    }

    private RiskReviewResponse mapToResponse(
            RiskReview review
    ) {
        DeliveryEvent event = review.getDeliveryEvent();
        PlatformUser reviewer = review.getReviewer();

        return new RiskReviewResponse(
                review.getId(),
                event.getId(),
                event.getReference(),
                reviewer.getId(),
                reviewer.getFullName(),
                review.getDecision(),
                review.getRecoveryAction(),
                review.getNotes(),
                review.getReviewedAt(),
                event.getStatus()
        );
    }

    @Transactional(readOnly = true)
    public List<RiskReviewResponse> getReviewHistory(
            UUID companyId,
            UUID eventId
    ) {
        boolean eventExists = eventRepository.existsByIdAndCompany_Id(
                eventId,
                companyId
        );

        if (!eventExists) {
            throw new ResourceNotFoundException(
                    "Delivery event not found with ID: " + eventId
            );
        }

        return reviewRepository
                .findByDeliveryEvent_IdAndDeliveryEvent_Company_IdOrderByReviewedAtAscIdAsc(
                        eventId,
                        companyId
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }
}