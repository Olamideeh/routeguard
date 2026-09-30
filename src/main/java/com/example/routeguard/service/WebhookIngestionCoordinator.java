package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventRequest;
import com.example.routeguard.dto.DeliveryEventResponse;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebhookIngestionCoordinator {

    private final WebhookIngestionService ingestionService;

    // Intentionally has no @Transactional annotation.
    public DeliveryEventResponse ingestEvent(
            String apiKey,
            String idempotencyKey,
            DeliveryEventRequest request
    ) {
        try {
            return ingestionService.ingestEvent(
                    apiKey,
                    idempotencyKey,
                    request
            );
        } catch (DataIntegrityViolationException exception) {
            if (!isIdempotencyConflict(exception)) {
                throw exception;
            }

            // The first call has rolled back.
            // This call starts a fresh transaction and finds
            // the event saved by the other request.
            return ingestionService.ingestEvent(
                    apiKey,
                    idempotencyKey,
                    request
            );
        }
    }

    private boolean isIdempotencyConflict(
            Throwable exception
    ) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof ConstraintViolationException violation
                    && "uk_company_idempotency_key".equals(
                    violation.getConstraintName()
            )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}