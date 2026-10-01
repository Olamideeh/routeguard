package com.example.routeguard.service;

import com.example.routeguard.dto.RiskReviewRequest;
import com.example.routeguard.dto.RiskReviewResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.entity.PlatformUser;
import com.example.routeguard.entity.RiskReview;
import com.example.routeguard.enums.*;
import com.example.routeguard.repository.DeliveryEventRepository;
import com.example.routeguard.repository.PlatformUserRepository;
import com.example.routeguard.repository.RiskReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.example.routeguard.exception.BusinessRuleException;
import org.junit.jupiter.params.provider.CsvSource;
import com.example.routeguard.enums.UserRole;
import com.example.routeguard.exception.ResourceNotFoundException;
import java.util.List;


import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskReviewServiceTest {

    @Mock
    private DeliveryEventRepository eventRepository;

    @Mock
    private PlatformUserRepository userRepository;

    @Mock
    private RiskReviewRepository reviewRepository;

    private RiskReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new RiskReviewService(
                eventRepository,
                userRepository,
                reviewRepository
        );
    }

    @Test
    void inconclusiveReviewKeepsEventUnderReview() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        DeliveryCompany company = DeliveryCompany.builder()
                .id(companyId)
                .status(CompanyStatus.ACTIVE)
                .build();

        PlatformUser reviewer = PlatformUser.builder()
                .id(reviewerId)
                .fullName("Daniel Okafor")
                .role(UserRole.RISK_REVIEWER)
                .company(company)
                .active(true)
                .build();

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-REVIEW-001")
                .company(company)
                .status(EvaluationStatus.SUSPICIOUS)
                .build();

        RiskReviewRequest request = new RiskReviewRequest(
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.MANUAL_INVESTIGATION,
                "Additional evidence is required."
        );

        when(userRepository.findById(reviewerId))
                .thenReturn(Optional.of(reviewer));

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.of(event));

        // Simulate the timestamp normally assigned during persistence.
        when(reviewRepository.save(any(RiskReview.class)))
                .thenAnswer(invocation -> {
                    RiskReview review = invocation.getArgument(0);
                    review.beforeInsert();
                    return review;
                });

        RiskReviewResponse response = reviewService.submitReview(
                companyId,
                eventId,
                reviewerId,
                request
        );

        assertAll(
                () -> assertEquals(
                        EvaluationStatus.UNDER_REVIEW,
                        event.getStatus()
                ),
                () -> assertEquals(
                        EvaluationStatus.UNDER_REVIEW,
                        response.eventStatus()
                ),
                () -> assertEquals(
                        ReviewDecision.INCONCLUSIVE,
                        response.decision()
                ),
                () -> assertEquals(
                        RecoveryAction.MANUAL_INVESTIGATION,
                        response.recoveryAction()
                ),
                () -> assertEquals(
                        reviewerId,
                        response.reviewerId()
                ),
                () -> assertEquals(
                        eventId,
                        response.deliveryEventId()
                ),
                () -> assertEquals(
                        request.notes(),
                        response.notes()
                ),
                () -> assertNotNull(response.reviewedAt())
        );

        verify(reviewRepository).save(any(RiskReview.class));
        verify(eventRepository).save(event);
    }
    @Test
    void reviewerFromAnotherCompanyIsRejected() {
        UUID requestingCompanyId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryCompany otherCompany = DeliveryCompany.builder()
                .id(UUID.randomUUID())
                .status(CompanyStatus.ACTIVE)
                .build();

        PlatformUser reviewer = PlatformUser.builder()
                .id(reviewerId)
                .role(UserRole.RISK_REVIEWER)
                .company(otherCompany)
                .active(true)
                .build();

        RiskReviewRequest request = new RiskReviewRequest(
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.MANUAL_INVESTIGATION,
                "Additional evidence is required."
        );

        when(userRepository.findById(reviewerId))
                .thenReturn(Optional.of(reviewer));

        assertThrows(
                AccessDeniedException.class,
                () -> reviewService.submitReview(
                        requestingCompanyId,
                        eventId,
                        reviewerId,
                        request
                )
        );

        verifyNoInteractions(eventRepository, reviewRepository);
    }
    @ParameterizedTest
    @EnumSource(
            value = ReviewDecision.class,
            names = {
                    "CONFIRMED_VALID",
                    "CONFIRMED_SUSPICIOUS"
            }
    )
    void confirmedReviewResolvesEvent(ReviewDecision decision) {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        DeliveryCompany company = DeliveryCompany.builder()
                .id(companyId)
                .status(CompanyStatus.ACTIVE)
                .build();

        PlatformUser reviewer = PlatformUser.builder()
                .id(reviewerId)
                .fullName("Daniel Okafor")
                .role(UserRole.RISK_REVIEWER)
                .company(company)
                .active(true)
                .build();

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-CONFIRMED-001")
                .company(company)
                .status(EvaluationStatus.UNDER_REVIEW)
                .build();

        RiskReviewRequest request = new RiskReviewRequest(
                decision,
                RecoveryAction.NO_ACTION,
                "Evidence reviewed and decision confirmed."
        );

        when(userRepository.findById(reviewerId))
                .thenReturn(Optional.of(reviewer));

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.of(event));

        when(reviewRepository.save(any(RiskReview.class)))
                .thenAnswer(invocation -> {
                    RiskReview review = invocation.getArgument(0);
                    review.beforeInsert();
                    return review;
                });

        RiskReviewResponse response = reviewService.submitReview(
                companyId,
                eventId,
                reviewerId,
                request
        );

        assertAll(
                () -> assertEquals(decision, response.decision()),
                () -> assertEquals(
                        EvaluationStatus.RESOLVED,
                        event.getStatus()
                ),
                () -> assertEquals(
                        EvaluationStatus.RESOLVED,
                        response.eventStatus()
                )
        );

        verify(reviewRepository).save(any(RiskReview.class));
        verify(eventRepository).save(event);
    }
    @Test
    void resolvedEventCannotBeReviewedAgain() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        DeliveryCompany company = DeliveryCompany.builder()
                .id(companyId)
                .status(CompanyStatus.ACTIVE)
                .build();

        PlatformUser reviewer = PlatformUser.builder()
                .id(reviewerId)
                .role(UserRole.RISK_REVIEWER)
                .company(company)
                .active(true)
                .build();

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .company(company)
                .status(EvaluationStatus.RESOLVED)
                .build();

        RiskReviewRequest request = new RiskReviewRequest(
                ReviewDecision.CONFIRMED_VALID,
                RecoveryAction.NO_ACTION,
                "Attempt to review an already resolved event."
        );

        when(userRepository.findById(reviewerId))
                .thenReturn(Optional.of(reviewer));

        when(eventRepository.findByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(Optional.of(event));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reviewService.submitReview(
                        companyId,
                        eventId,
                        reviewerId,
                        request
                )
        );

        assertAll(
                () -> assertEquals(
                        "Only SUSPICIOUS, REVIEW_REQUIRED, "
                                + "or UNDER_REVIEW events can be reviewed",
                        exception.getMessage()
                ),
                () -> assertEquals(
                        EvaluationStatus.RESOLVED,
                        event.getStatus()
                )
        );

        verifyNoInteractions(reviewRepository);

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @ParameterizedTest
    @CsvSource({
            "RISK_REVIEWER, false",
            "OPERATIONS_OFFICER, true",
            "COMPANY_ADMIN, true"
    })
    void unauthorizedReviewerIsRejected(
            UserRole role,
            boolean active
    ) {
        UUID companyId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        DeliveryCompany company = DeliveryCompany.builder()
                .id(companyId)
                .status(CompanyStatus.ACTIVE)
                .build();

        PlatformUser reviewer = PlatformUser.builder()
                .id(reviewerId)
                .role(role)
                .company(company)
                .active(active)
                .build();

        RiskReviewRequest request = new RiskReviewRequest(
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.MANUAL_INVESTIGATION,
                "Additional evidence is required."
        );

        when(userRepository.findById(reviewerId))
                .thenReturn(Optional.of(reviewer));

        assertThrows(
                AccessDeniedException.class,
                () -> reviewService.submitReview(
                        companyId,
                        UUID.randomUUID(),
                        reviewerId,
                        request
                )
        );

        verifyNoInteractions(eventRepository, reviewRepository);
    }
    @Test
    void inconclusiveReviewRequiresManualInvestigation() {
        UUID companyId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        DeliveryCompany company = DeliveryCompany.builder()
                .id(companyId)
                .status(CompanyStatus.ACTIVE)
                .build();

        PlatformUser reviewer = PlatformUser.builder()
                .id(reviewerId)
                .role(UserRole.RISK_REVIEWER)
                .company(company)
                .active(true)
                .build();

        RiskReviewRequest request = new RiskReviewRequest(
                ReviewDecision.INCONCLUSIVE,
                RecoveryAction.RETURN_TO_SENDER,
                "The evidence is still inconclusive."
        );

        when(userRepository.findById(reviewerId))
                .thenReturn(Optional.of(reviewer));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> reviewService.submitReview(
                        companyId,
                        UUID.randomUUID(),
                        reviewerId,
                        request
                )
        );

        assertEquals(
                "An inconclusive review requires MANUAL_INVESTIGATION",
                exception.getMessage()
        );

        verifyNoInteractions(eventRepository, reviewRepository);
    }
    @Test
    void accessibleEventWithoutReviewsReturnsEmptyHistory() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(eventRepository.existsByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(true);

        when(reviewRepository
                .findByDeliveryEvent_IdAndDeliveryEvent_Company_IdOrderByReviewedAtAscIdAsc(
                        eventId,
                        companyId
                ))
                .thenReturn(List.of());

        List<RiskReviewResponse> response =
                reviewService.getReviewHistory(
                        companyId,
                        eventId
                );

        assertTrue(response.isEmpty());

        verifyNoInteractions(userRepository);

        verify(reviewRepository, never())
                .save(any(RiskReview.class));

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @Test
    void unavailableEventReviewHistoryIsRejected() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(eventRepository.existsByIdAndCompany_Id(
                eventId,
                companyId
        )).thenReturn(false);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> reviewService.getReviewHistory(
                        companyId,
                        eventId
                )
        );

        assertEquals(
                "Delivery event not found with ID: " + eventId,
                exception.getMessage()
        );

        verifyNoInteractions(
                reviewRepository,
                userRepository
        );

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
}