package com.example.routeguard.service;

import com.example.routeguard.entity.DeliveryEvaluation;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeliveryEvaluationService {

    private final GpsDistanceService gpsDistanceService;
    private final DeliveryEventRepository eventRepository;
    private final BigDecimal maximumGpsDistanceMetres;

    public DeliveryEvaluationService(
            GpsDistanceService gpsDistanceService,
            DeliveryEventRepository eventRepository,
            @Value("${routeguard.evaluation.maximum-gps-distance-metres:200}")
            BigDecimal maximumGpsDistanceMetres
    ) {
        if (maximumGpsDistanceMetres == null
                || maximumGpsDistanceMetres.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Maximum GPS distance must be greater than zero"
            );
        }

        this.gpsDistanceService = gpsDistanceService;
        this.eventRepository = eventRepository;
        this.maximumGpsDistanceMetres = maximumGpsDistanceMetres;
    }

    public DeliveryEvaluation evaluate(DeliveryEvent event) {
        if (event == null
                || event.getId() == null
                || event.getCompany() == null
                || event.getCompany().getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted event with a company is required"
            );
        }

        if (event.getEventType() == DeliveryEventType.DELIVERY_FAILED) {
            return evaluateFailedDelivery(event);
        }

        if (event.getEventType() != DeliveryEventType.DELIVERED) {
            throw new IllegalArgumentException(
                    "Unsupported delivery event type: "
                            + event.getEventType()
            );
        }

        Set<DecisionReasonCode> reasons =
                EnumSet.noneOf(DecisionReasonCode.class);

        BigDecimal gpsDistance = null;
        Boolean photoReused = null;
        int riskScore = 0;
        boolean suspicious = false;

        // GPS evidence
        boolean gpsMissing =
                event.getDeliveryLatitude() == null
                        || event.getDeliveryLongitude() == null
                        || event.getExpectedLatitude() == null
                        || event.getExpectedLongitude() == null;

        if (gpsMissing) {
            reasons.add(DecisionReasonCode.GPS_EVIDENCE_MISSING);
        } else {
            gpsDistance = gpsDistanceService.calculateDistanceMetres(
                    event.getDeliveryLatitude(),
                    event.getDeliveryLongitude(),
                    event.getExpectedLatitude(),
                    event.getExpectedLongitude()
            );

            if (gpsDistance.compareTo(maximumGpsDistanceMetres) > 0) {
                reasons.add(DecisionReasonCode.GPS_DISTANCE_EXCEEDED);
                riskScore += 30;
                suspicious = true;
            }
        }

        // OTP evidence
        if (event.getOtpVerified() == null) {
            reasons.add(DecisionReasonCode.OTP_EVIDENCE_MISSING);
        } else if (!event.getOtpVerified()) {
            reasons.add(DecisionReasonCode.OTP_INVALID);
            riskScore += 40;
            suspicious = true;
        }

        // Photo evidence: exclude this event from the reuse check.
        if (event.getProofPhotoHash() == null
                || event.getProofPhotoHash().isBlank()) {
            reasons.add(DecisionReasonCode.PHOTO_EVIDENCE_MISSING);
        } else {
            photoReused = eventRepository
                    .existsByCompany_IdAndProofPhotoHashAndIdNot(
                            event.getCompany().getId(),
                            event.getProofPhotoHash(),
                            event.getId()
                    );

            if (photoReused) {
                reasons.add(DecisionReasonCode.PHOTO_REUSED);
                riskScore += 30;
                suspicious = true;
            }
        }

        EvaluationDecision decision;
        RecoveryAction recoveryAction;
        String explanation;

        if (suspicious) {
            decision = EvaluationDecision.SUSPICIOUS;
            recoveryAction = RecoveryAction.MANUAL_INVESTIGATION;
            explanation = "Evidence checks failed: "
                    + describeReasons(reasons);
        } else if (!reasons.isEmpty()) {
            decision = EvaluationDecision.REVIEW_REQUIRED;
            recoveryAction = RecoveryAction.MANUAL_INVESTIGATION;
            explanation = "Evidence is incomplete: "
                    + describeReasons(reasons);
        } else {
            decision = EvaluationDecision.VERIFIED;
            recoveryAction = RecoveryAction.NO_ACTION;
            reasons.add(DecisionReasonCode.ALL_EVIDENCE_VERIFIED);
            explanation = "GPS distance is within the configured limit, "
                    + "OTP is verified, and the proof photo is not reused.";
        }

        return DeliveryEvaluation.builder()
                .deliveryEvent(event)
                .decision(decision)
                .reasonCodes(reasons)
                .recoveryAction(recoveryAction)
                .gpsDistanceMetres(gpsDistance)
                .photoReused(photoReused)
                .riskScore(riskScore)
                .decisionExplanation(explanation)
                .build();
    }

    private String describeReasons(
            Set<DecisionReasonCode> reasons
    ) {
        return reasons.stream()
                .map(DecisionReasonCode::name)
                .collect(Collectors.joining(", "));
    }
    private DeliveryEvaluation evaluateFailedDelivery(
            DeliveryEvent event
    ) {
        Set<DecisionReasonCode> reasons =
                EnumSet.noneOf(DecisionReasonCode.class);

        boolean reasonMissing =
                event.getFailureReason() == null
                        || event.getFailureReason().isBlank();

        boolean contactMissing =
                !Boolean.TRUE.equals(
                        event.getCustomerContactAttempted()
                );

        if (reasonMissing) {
            reasons.add(DecisionReasonCode.FAILURE_REASON_MISSING);
        }

        if (contactMissing) {
            reasons.add(DecisionReasonCode.CONTACT_ATTEMPT_MISSING);
        }

        EvaluationDecision decision;
        RecoveryAction recoveryAction;
        String explanation;

        if (reasonMissing) {
            decision = EvaluationDecision.REVIEW_REQUIRED;
            recoveryAction = RecoveryAction.MANUAL_INVESTIGATION;
            explanation = "Failed-delivery report is incomplete: "
                    + describeReasons(reasons);
        } else if (contactMissing) {
            decision = EvaluationDecision.REVIEW_REQUIRED;
            recoveryAction = RecoveryAction.CONTACT_CUSTOMER;
            explanation = "A failure reason was supplied, but a "
                    + "customer-contact attempt was not confirmed.";
        } else {
            // A reason and contact flag alone do not independently
            // prove that the reported delivery failure is valid.
            decision = EvaluationDecision.REVIEW_REQUIRED;
            recoveryAction = RecoveryAction.MANUAL_INVESTIGATION;
            reasons.add(DecisionReasonCode.INSUFFICIENT_EVIDENCE);
            explanation = "A failure reason and customer-contact attempt "
                    + "were recorded. Review is required to confirm "
                    + "the failure and select a recovery action.";
        }

        return DeliveryEvaluation.builder()
                .deliveryEvent(event)
                .decision(decision)
                .reasonCodes(reasons)
                .recoveryAction(recoveryAction)
                .riskScore(0)
                .decisionExplanation(explanation)
                .build();
    }
}