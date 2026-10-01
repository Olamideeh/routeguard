package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEvaluationResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvaluation;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.*;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import com.example.routeguard.repository.DeliveryEvaluationRepository;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5439/routeguard_test_db",
        "spring.datasource.username=routeguard_test_user",
        "spring.datasource.password=routeguard_test_password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class DeliveryEventProcessingConcurrencyTest {

    @Autowired
    private DeliveryEventProcessingService processingService;

    @Autowired
    private DeliveryCompanyRepository companyRepository;

    @Autowired
    private DeliveryEventRepository eventRepository;

    @Autowired
    private DeliveryEvaluationRepository evaluationRepository;

    @MockitoBean
    private DeliveryEvaluationService evaluationService;

    @Test
    void concurrentProcessingSavesOneEvaluation() throws Exception {
        DeliveryCompany company = companyRepository.saveAndFlush(
                DeliveryCompany.builder()
                        .name("Processing Test Company")
                        .companyCode("TEST-" + UUID.randomUUID())
                        .countryCode("NG")
                        .status(CompanyStatus.ACTIVE)
                        .build()
        );

        DeliveryEvent event = eventRepository.saveAndFlush(
                DeliveryEvent.builder()
                        .reference("EVT-" + UUID.randomUUID())
                        .externalDeliveryId("DEL-PROCESSING-001")
                        .idempotencyKey(UUID.randomUUID().toString())
                        .eventType(DeliveryEventType.DELIVERED)
                        .eventTimestamp(Instant.now())
                        .riderId("RIDER-202")
                        .customerId("CUSTOMER-501")
                        .status(EvaluationStatus.RECEIVED)
                        .company(company)
                        .build()
        );

        when(evaluationService.evaluate(any(DeliveryEvent.class)))
                .thenAnswer(invocation -> {
                    DeliveryEvent processingEvent =
                            invocation.getArgument(0);

                    return DeliveryEvaluation.builder()
                            .deliveryEvent(processingEvent)
                            .decision(EvaluationDecision.VERIFIED)
                            .reasonCodes(
                                    Set.of(
                                            DecisionReasonCode.ALL_EVIDENCE_VERIFIED
                                    )
                            )
                            .recoveryAction(RecoveryAction.NO_ACTION)
                            .photoReused(false)
                            .riskScore(0)
                            .decisionExplanation(
                                    "Controlled result for concurrency test."
                            )
                            .build();
                });

        CyclicBarrier startBarrier = new CyclicBarrier(2);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Callable<DeliveryEvaluationResponse> process =
                    () -> {
                        startBarrier.await(10, TimeUnit.SECONDS);

                        return processingService.processEvent(
                                company.getId(),
                                event.getId()
                        );
                    };

            Future<DeliveryEvaluationResponse> first =
                    executor.submit(process);

            Future<DeliveryEvaluationResponse> second =
                    executor.submit(process);

            assertAll(
                    "Both processing calls must succeed",
                    () -> assertNotNull(
                            first.get(20, TimeUnit.SECONDS)
                    ),
                    () -> assertNotNull(
                            second.get(20, TimeUnit.SECONDS)
                    )
            );

            DeliveryEvaluationResponse firstResponse = first.get();
            DeliveryEvaluationResponse secondResponse = second.get();

            DeliveryEvaluation storedEvaluation =
                    evaluationRepository
                            .findByDeliveryEvent_Id(event.getId())
                            .orElseThrow();

            DeliveryEvent storedEvent = eventRepository
                    .findById(event.getId())
                    .orElseThrow();

            assertAll(
                    () -> assertNotNull(firstResponse.id()),
                    () -> assertEquals(
                            firstResponse.id(),
                            secondResponse.id()
                    ),
                    () -> assertEquals(
                            storedEvaluation.getId(),
                            firstResponse.id()
                    ),
                    () -> assertEquals(
                            1L,
                            evaluationRepository.count()
                    ),
                    () -> assertEquals(
                            EvaluationStatus.VERIFIED,
                            storedEvent.getStatus()
                    )
            );

            verify(evaluationService, times(1))
                    .evaluate(any(DeliveryEvent.class));
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}