package com.example.routeguard.controller;

import com.example.routeguard.dto.DeliveryEventRequest;
import com.example.routeguard.dto.DeliveryEventResponse;
import com.example.routeguard.service.WebhookIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class DeliveryWebhookController {

    private final WebhookIngestionService ingestionService;

    @PostMapping("/delivery-events")
    public ResponseEntity<DeliveryEventResponse>
    receiveDeliveryEvent(
            @RequestHeader("X-API-Key")
            String apiKey,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid @RequestBody
            DeliveryEventRequest request
    ) {
        DeliveryEventResponse response =
                ingestionService.ingestEvent(
                        apiKey,
                        idempotencyKey,
                        request
                );

        HttpStatus status = response.idempotentReplay()
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity
                .status(status)
                .body(response);
    }
}