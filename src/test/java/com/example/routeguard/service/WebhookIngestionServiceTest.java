package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventRequest;
import com.example.routeguard.dto.DeliveryEventResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;
import com.example.routeguard.exception.BusinessRuleException;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;


import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookIngestionServiceTest {

    @Mock
    private ApiCredentialService credentialService;

    @Mock
    private DeliveryEventRepository eventRepository;

    private JsonMapper objectMapper;
    private WebhookIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        ingestionService = new WebhookIngestionService(
                credentialService,
                eventRepository,
                objectMapper
        );
    }

    @Test
    void identicalReplayReturnsExistingEventWithoutSaving()
            throws Exception {

        // Arrange: prepare the company and incoming request.
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-1002",
                DeliveryEventType.DELIVERED,
                Instant.parse("2026-09-30T20:00:00Z"),
                "RIDER-202",
                "CUSTOMER-501",
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                true
        );

        // Give the stored event a matching payload hash.
        String payloadHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(
                                objectMapper.writeValueAsBytes(request)
                        )
        );

        Instant createdAt =
                Instant.parse("2026-09-30T20:01:00Z");

        DeliveryEvent existingEvent = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-TEST1002")
                .externalDeliveryId("DEL-1002")
                .idempotencyKey("delivery-event-1002")
                .requestPayloadHash(payloadHash)
                .eventType(DeliveryEventType.DELIVERED)
                .status(EvaluationStatus.RECEIVED)
                .company(company)
                .createdAt(createdAt)
                .build();

        when(credentialService.authenticateApiKey("test-api-key"))
                .thenReturn(company);

        when(eventRepository.findByCompany_IdAndIdempotencyKey(
                companyId,
                "delivery-event-1002"
        )).thenReturn(Optional.of(existingEvent));

        // Act: replay the request.
        DeliveryEventResponse response =
                ingestionService.ingestEvent(
                        "test-api-key",
                        "delivery-event-1002",
                        request
                );

        // Assert: return the existing event, without inserting another.
        assertAll(
                () -> assertEquals(eventId, response.id()),
                () -> assertEquals(
                        "EVT-TEST1002",
                        response.reference()
                ),
                () -> assertTrue(response.idempotentReplay()),
                () -> assertEquals(createdAt, response.createdAt())
        );

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @Test
    void differentPayloadWithSameKeyIsRejected() {
        UUID companyId = UUID.randomUUID();

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-1002",
                DeliveryEventType.DELIVERED,
                Instant.parse("2026-09-30T20:00:00Z"),
                "RIDER-999",
                "CUSTOMER-501",
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                true
        );

        // This hash represents a different, previously stored payload.
        DeliveryEvent existingEvent = DeliveryEvent.builder()
                .id(UUID.randomUUID())
                .idempotencyKey("delivery-event-1002")
                .requestPayloadHash("0".repeat(64))
                .company(company)
                .build();

        when(credentialService.authenticateApiKey("test-api-key"))
                .thenReturn(company);

        when(eventRepository.findByCompany_IdAndIdempotencyKey(
                companyId,
                "delivery-event-1002"
        )).thenReturn(Optional.of(existingEvent));

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> ingestionService.ingestEvent(
                        "test-api-key",
                        "delivery-event-1002",
                        request
                )
        );

        assertEquals(
                "Idempotency key has already been used "
                        + "with a different request payload",
                exception.getMessage()
        );

        verify(eventRepository, never())
                .save(any(DeliveryEvent.class));
    }
    @Test
    void newEventIsSavedWithPayloadHash() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-30T20:01:00Z");

        DeliveryCompany company = new DeliveryCompany();
        company.setId(companyId);

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-1003",
                DeliveryEventType.DELIVERED,
                Instant.parse("2026-09-30T20:00:00Z"),
                "RIDER-202",
                "CUSTOMER-501",
                null,
                null,
                null,
                null,
                true,
                null,
                null,
                true
        );

        when(credentialService.authenticateApiKey("test-api-key"))
                .thenReturn(company);

        when(eventRepository.findByCompany_IdAndIdempotencyKey(
                companyId,
                "delivery-event-1003"
        )).thenReturn(Optional.empty());

        // Simulate the ID and timestamp normally assigned during persistence.
        when(eventRepository.save(any(DeliveryEvent.class)))
                .thenAnswer(invocation -> {
                    DeliveryEvent event = invocation.getArgument(0);
                    event.setId(eventId);
                    event.setCreatedAt(createdAt);
                    return event;
                });

        DeliveryEventResponse response =
                ingestionService.ingestEvent(
                        "test-api-key",
                        " delivery-event-1003 ",
                        request
                );

        ArgumentCaptor<DeliveryEvent> captor =
                ArgumentCaptor.forClass(DeliveryEvent.class);

        verify(eventRepository).save(captor.capture());

        DeliveryEvent savedEvent = captor.getValue();

        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(objectMapper.writeValueAsBytes(request))
        );

        assertAll(
                () -> assertEquals(eventId, response.id()),
                () -> assertFalse(response.idempotentReplay()),
                () -> assertEquals(
                        EvaluationStatus.RECEIVED,
                        response.status()
                ),
                () -> assertEquals(
                        "delivery-event-1003",
                        savedEvent.getIdempotencyKey()
                ),
                () -> assertEquals(
                        expectedHash,
                        savedEvent.getRequestPayloadHash()
                ),
                () -> assertSame(company, savedEvent.getCompany()),
                () -> assertEquals(
                        request.externalDeliveryId(),
                        savedEvent.getExternalDeliveryId()
                ),
                () -> assertEquals(
                        request.eventType(),
                        savedEvent.getEventType()
                ),
                () -> assertEquals(
                        request.riderId(),
                        savedEvent.getRiderId()
                ),
                () -> assertNotNull(response.reference()),
                () -> assertTrue(
                        response.reference().startsWith("EVT-")
                )
        );
    }
    @Test
    void incompleteDeliveryCoordinatesAreRejected() {
        DeliveryCompany company = new DeliveryCompany();
        company.setId(UUID.randomUUID());

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-1004",
                DeliveryEventType.DELIVERED,
                Instant.parse("2026-09-30T20:00:00Z"),
                "RIDER-202",
                "CUSTOMER-501",
                new BigDecimal("6.4541000"),
                null,
                null,
                null,
                true,
                null,
                null,
                true
        );

        when(credentialService.authenticateApiKey("test-api-key"))
                .thenReturn(company);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> ingestionService.ingestEvent(
                        "test-api-key",
                        "delivery-event-1004",
                        request
                )
        );

        assertEquals(
                "Delivery latitude and longitude "
                        + "must be supplied together",
                exception.getMessage()
        );

        // Invalid evidence must be rejected before accessing event storage.
        verifyNoInteractions(eventRepository);
    }
    @Test
    void failedDeliveryWithoutReasonIsRejected() {
        DeliveryCompany company = new DeliveryCompany();
        company.setId(UUID.randomUUID());

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-1005",
                DeliveryEventType.DELIVERY_FAILED,
                Instant.parse("2026-09-30T20:00:00Z"),
                "RIDER-202",
                "CUSTOMER-501",
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                true
        );

        when(credentialService.authenticateApiKey("test-api-key"))
                .thenReturn(company);

        BusinessRuleException exception = assertThrows(
                BusinessRuleException.class,
                () -> ingestionService.ingestEvent(
                        "test-api-key",
                        "delivery-event-1005",
                        request
                )
        );

        assertEquals(
                "Failure reason is required for a failed delivery",
                exception.getMessage()
        );

        verifyNoInteractions(eventRepository);
    }
}