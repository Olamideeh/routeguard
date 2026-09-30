package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventRequest;
import com.example.routeguard.dto.DeliveryEventResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import jakarta.persistence.EntityManager;
import java.util.concurrent.atomic.AtomicInteger;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5439/routeguard_test_db",
        "spring.datasource.username=routeguard_test_user",
        "spring.datasource.password=routeguard_test_password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class WebhookIngestionConcurrencyTest {

    @Autowired
    private WebhookIngestionCoordinator ingestionCoordinator;

    @Autowired
    private DeliveryCompanyRepository companyRepository;

    @MockitoSpyBean
    private DeliveryEventRepository eventRepository;

    @MockitoBean
    private ApiCredentialService credentialService;

    @Autowired
    private EntityManager entityManager;

    @Test
    void simultaneousIdenticalRequestsCreateOneEventAndReturnReplay()
            throws Exception {

        DeliveryCompany company = companyRepository.saveAndFlush(
                DeliveryCompany.builder()
                        .name("Concurrency Test Company")
                        .companyCode(
                                "TEST-" + UUID.randomUUID()
                        )
                        .countryCode("NG")
                        .status(CompanyStatus.ACTIVE)
                        .build()
        );

        String idempotencyKey =
                "concurrent-" + UUID.randomUUID();

        DeliveryEventRequest request = new DeliveryEventRequest(
                "DEL-CONCURRENT-001",
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

        CyclicBarrier lookupBarrier = new CyclicBarrier(2);
        AtomicInteger lookupCount = new AtomicInteger();

        // Both requests must complete the database lookup
        // before either is allowed to continue.
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
                    .setParameter("companyId", requestedCompanyId)
                    .setParameter("idempotencyKey", requestedKey)
                    .getResultList()
                    .stream()
                    .findFirst();

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
            Callable<DeliveryEventResponse> sendRequest =
                    () -> ingestionCoordinator.ingestEvent(
                            "test-api-key",
                            idempotencyKey,
                            request
                    );

            Future<DeliveryEventResponse> first =
                    executor.submit(sendRequest);

            Future<DeliveryEventResponse> second =
                    executor.submit(sendRequest);

            // Check both results, even if one request fails.
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
                            idempotencyKey.equals(
                                    event.getIdempotencyKey()
                            )
                    )
                    .count();

            assertAll(
                    () -> assertEquals(1L, storedEvents),
                    () -> assertEquals(
                            firstResponse.id(),
                            secondResponse.id()
                    ),
                    () -> assertEquals(
                            firstResponse.reference(),
                            secondResponse.reference()
                    ),
                    () -> assertNotEquals(
                            firstResponse.idempotentReplay(),
                            secondResponse.idempotentReplay()
                    )
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}