package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventRequest;
import com.example.routeguard.dto.DeliveryEventResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import com.example.routeguard.repository.DeliveryEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5439/routeguard_test_db",
        "spring.datasource.username=routeguard_test_user",
        "spring.datasource.password=routeguard_test_password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class WebhookIngestionRealCredentialConcurrencyTest {

    @Autowired
    private WebhookIngestionCoordinator ingestionCoordinator;

    @Autowired
    private ApiCredentialService credentialService;

    @Autowired
    private DeliveryCompanyRepository companyRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoSpyBean
    private DeliveryEventRepository eventRepository;

    @Test
    void simultaneousRequestsWithRealCredentialReturnSameEvent()
            throws Exception {

        // Create an active company in the test database.
        DeliveryCompany company = companyRepository.saveAndFlush(
                DeliveryCompany.builder()
                        .name("Real Credential Test Company")
                        .companyCode(
                                "TEST-" + UUID.randomUUID()
                        )
                        .countryCode("NG")
                        .status(CompanyStatus.ACTIVE)
                        .build()
        );

        // Generate a real API credential for this company.
        String apiKey = credentialService
                .createCredential(company.getId())
                .apiKey();

        String idempotencyKey =
                "concurrent-" + UUID.randomUUID();

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-REAL-CONCURRENT-001",
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

        CyclicBarrier lookupBarrier = new CyclicBarrier(2);
        AtomicInteger lookupCount = new AtomicInteger();

        // Perform a real database lookup, then pause the first
        // two calls so both finish their lookup before saving.
        doAnswer(invocation -> {
            UUID requestedCompanyId = invocation.getArgument(0);
            String requestedKey = invocation.getArgument(1);

            Optional<DeliveryEvent> result = entityManager
                    .createQuery("""
                            SELECT event
                            FROM DeliveryEvent event
                            WHERE event.company.id = :companyId
                              AND event.idempotencyKey = :idempotencyKey
                            """, DeliveryEvent.class)
                    .setParameter(
                            "companyId",
                            requestedCompanyId
                    )
                    .setParameter(
                            "idempotencyKey",
                            requestedKey
                    )
                    .getResultList()
                    .stream()
                    .findFirst();

            // A retry must proceed without waiting at the barrier.
            if (lookupCount.incrementAndGet() <= 2) {
                lookupBarrier.await(10, TimeUnit.SECONDS);
            }

            return result;
        }).when(eventRepository)
                .findByCompany_IdAndIdempotencyKey(
                        company.getId(),
                        idempotencyKey
                );

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            // Define the task using the real API key.
            Callable<DeliveryEventResponse> sendRequest =
                    () -> ingestionCoordinator.ingestEvent(
                            apiKey,
                            idempotencyKey,
                            request
                    );

            // Run the same task on two worker threads.
            Future<DeliveryEventResponse> first =
                    executor.submit(sendRequest);

            Future<DeliveryEventResponse> second =
                    executor.submit(sendRequest);

            assertAll(
                    "Both concurrent requests must succeed",
                    () -> assertNotNull(
                            first.get(20, TimeUnit.SECONDS)
                    ),
                    () -> assertNotNull(
                            second.get(20, TimeUnit.SECONDS)
                    )
            );

            DeliveryEventResponse firstResponse = first.get();
            DeliveryEventResponse secondResponse = second.get();

            long storedEvents = eventRepository.findAll()
                    .stream()
                    .filter(event ->
                            company.getId().equals(
                                    event.getCompany().getId()
                            )
                                    && idempotencyKey.equals(
                                    event.getIdempotencyKey()
                            )
                    )
                    .count();

            assertAll(
                    () -> assertEquals(
                            1L,
                            storedEvents,
                            "Only one event should be stored"
                    ),
                    () -> assertEquals(
                            firstResponse.id(),
                            secondResponse.id(),
                            "Both responses should have the same event ID"
                    ),
                    () -> assertEquals(
                            firstResponse.reference(),
                            secondResponse.reference(),
                            "Both responses should have the same reference"
                    ),
                    () -> assertNotEquals(
                            firstResponse.idempotentReplay(),
                            secondResponse.idempotentReplay(),
                            "One response should be new and the other a replay"
                    )
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}