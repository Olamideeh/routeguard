package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventRequest;
import com.example.routeguard.dto.DeliveryEventResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.exception.BusinessRuleException;
import com.example.routeguard.repository.DeliveryEventRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebhookIngestionService {

    private final ApiCredentialService credentialService;
    private final DeliveryEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeliveryEventResponse ingestEvent(
            String apiKey,
            String idempotencyKey,
            DeliveryEventRequest request
    ) {
        DeliveryCompany company =
                credentialService.authenticateApiKey(apiKey);

        validateIdempotencyKey(idempotencyKey);
        validateEvidenceFields(request);

        String normalizedIdempotencyKey =
                idempotencyKey.trim();

        String payloadHash = generatePayloadHash(request);

        return eventRepository
                .findByCompany_IdAndIdempotencyKey(
                        company.getId(),
                        normalizedIdempotencyKey
                )
                .map(existingEvent -> {
                    validateIdempotentReplay(
                            existingEvent,
                            payloadHash
                    );

                    return mapToResponse(existingEvent, true);
                })
                .orElseGet(() ->
                        createEvent(
                                company,
                                normalizedIdempotencyKey,
                                payloadHash,
                                request
                        )
                );
    }

    private DeliveryEventResponse createEvent(
            DeliveryCompany company,
            String idempotencyKey,
            String payloadHash,
            DeliveryEventRequest request
    ) {
        DeliveryEvent event = DeliveryEvent.builder()
                .reference(generateReference())
                .externalDeliveryId(
                        request.externalDeliveryId()
                )
                .idempotencyKey(idempotencyKey)
                .requestPayloadHash(payloadHash)
                .eventType(request.eventType())
                .eventTimestamp(request.eventTimestamp())
                .riderId(request.riderId())
                .customerId(request.customerId())
                .deliveryLatitude(
                        request.deliveryLatitude()
                )
                .deliveryLongitude(
                        request.deliveryLongitude()
                )
                .expectedLatitude(
                        request.expectedLatitude()
                )
                .expectedLongitude(
                        request.expectedLongitude()
                )
                .otpVerified(request.otpVerified())
                .proofPhotoHash(
                        request.proofPhotoHash()
                )
                .failureReason(
                        request.failureReason()
                )
                .customerContactAttempted(
                        request.customerContactAttempted()
                )
                .status(EvaluationStatus.RECEIVED)
                .company(company)
                .build();

        DeliveryEvent savedEvent =
                eventRepository.save(event);

        return mapToResponse(savedEvent, false);
    }

    private void validateIdempotencyKey(
            String idempotencyKey
    ) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new BusinessRuleException(
                    "Idempotency-Key header is required"
            );
        }

        if (idempotencyKey.trim().length() > 100) {
            throw new BusinessRuleException(
                    "Idempotency-Key cannot exceed 100 characters"
            );
        }
    }

    private void validateEvidenceFields(
            DeliveryEventRequest request
    ) {
        boolean deliveryCoordinatesIncomplete =
                (request.deliveryLatitude() == null)
                        != (request.deliveryLongitude() == null);

        if (deliveryCoordinatesIncomplete) {
            throw new BusinessRuleException(
                    "Delivery latitude and longitude "
                            + "must be supplied together"
            );
        }

        boolean expectedCoordinatesIncomplete =
                (request.expectedLatitude() == null)
                        != (request.expectedLongitude() == null);

        if (expectedCoordinatesIncomplete) {
            throw new BusinessRuleException(
                    "Expected latitude and longitude "
                            + "must be supplied together"
            );
        }

        if (request.eventType()
                == DeliveryEventType.DELIVERY_FAILED
                && (request.failureReason() == null
                || request.failureReason().isBlank())) {
            throw new BusinessRuleException(
                    "Failure reason is required "
                            + "for a failed delivery"
            );
        }
    }

    private void validateIdempotentReplay(
            DeliveryEvent existingEvent,
            String suppliedPayloadHash
    ) {
        if (existingEvent.getRequestPayloadHash() != null
                && !existingEvent.getRequestPayloadHash()
                .equals(suppliedPayloadHash)) {
            throw new BusinessRuleException(
                    "Idempotency key has already been used "
                            + "with a different request payload"
            );
        }
    }

    private String generatePayloadHash(
            DeliveryEventRequest request
    ) {
        try {
            byte[] payload =
                    objectMapper.writeValueAsBytes(request);

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(
                    digest.digest(payload)
            );
        } catch (
                JacksonException
                | NoSuchAlgorithmException exception
        ) {
            throw new IllegalStateException(
                    "Unable to generate request payload hash",
                    exception
            );
        }

    }

    private String generateReference() {
        return "EVT-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private DeliveryEventResponse mapToResponse(
            DeliveryEvent event,
            boolean idempotentReplay
    ) {
        return new DeliveryEventResponse(
                event.getId(),
                event.getReference(),
                event.getExternalDeliveryId(),
                event.getEventType(),
                event.getStatus(),
                idempotentReplay,
                event.getCreatedAt()
        );
    }
}