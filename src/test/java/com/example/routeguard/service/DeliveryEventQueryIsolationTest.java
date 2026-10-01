package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventPageResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.entity.DeliveryEvaluation;
import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.repository.DeliveryEvaluationRepository;

import java.util.Set;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5439/routeguard_test_db",
        "spring.datasource.username=routeguard_test_user",
        "spring.datasource.password=routeguard_test_password",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@Transactional
class DeliveryEventQueryIsolationTest {

    @Autowired
    private DeliveryEventQueryService queryService;

    @Autowired
    private DeliveryCompanyRepository companyRepository;

    @Autowired
    private DeliveryEventRepository eventRepository;

    @Autowired
    private DeliveryEventProcessingService processingService;

    @Autowired
    private DeliveryEvaluationRepository evaluationRepository;

    @Autowired
    private RiskReviewService reviewService;


    @Test
    void listingExcludesOtherCompaniesAndOtherStatuses() {
        DeliveryCompany companyA = createCompany("Company A");
        DeliveryCompany companyB = createCompany("Company B");

        DeliveryEvent expectedEvent = createEvent(
                companyA,
                EvaluationStatus.SUSPICIOUS
        );

        createEvent(companyA, EvaluationStatus.RECEIVED);
        createEvent(companyB, EvaluationStatus.SUSPICIOUS);

        DeliveryEventPageResponse response =
                queryService.getEvents(
                        companyA.getId(),
                        EvaluationStatus.SUSPICIOUS,
                        0,
                        10
                );

        assertAll(
                () -> assertEquals(1L, response.totalElements()),
                () -> assertEquals(1, response.content().size()),
                () -> assertEquals(
                        expectedEvent.getId(),
                        response.content().get(0).id()
                ),
                () -> assertEquals(
                        EvaluationStatus.SUSPICIOUS,
                        response.content().get(0).status()
                )
        );
    }

    private DeliveryCompany createCompany(String name) {
        return companyRepository.saveAndFlush(
                DeliveryCompany.builder()
                        .name(name)
                        .companyCode("TEST-" + UUID.randomUUID())
                        .countryCode("NG")
                        .status(CompanyStatus.ACTIVE)
                        .build()
        );
    }

    private DeliveryEvent createEvent(
            DeliveryCompany company,
            EvaluationStatus status
    ) {
        return eventRepository.saveAndFlush(
                DeliveryEvent.builder()
                        .reference("EVT-" + UUID.randomUUID())
                        .externalDeliveryId(
                                "DEL-" + UUID.randomUUID()
                        )
                        .idempotencyKey(UUID.randomUUID().toString())
                        .eventType(DeliveryEventType.DELIVERED)
                        .eventTimestamp(Instant.now())
                        .riderId("RIDER-202")
                        .customerId("CUSTOMER-501")
                        .status(status)
                        .company(company)
                        .build()
        );
    }
    @Test
    void processingRejectsAnotherCompanyEvent() {
        DeliveryCompany companyA = createCompany("Company A");
        DeliveryCompany companyB = createCompany("Company B");

        DeliveryEvent event = createEvent(
                companyB,
                EvaluationStatus.RECEIVED
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> processingService.processEvent(
                        companyA.getId(),
                        event.getId()
                )
        );
    }
    @Test
    void retrievalRejectsAnotherCompanyEvaluation() {
        DeliveryCompany companyA = createCompany("Company A");
        DeliveryCompany companyB = createCompany("Company B");

        DeliveryEvent event = createEvent(
                companyB,
                EvaluationStatus.REVIEW_REQUIRED
        );

        evaluationRepository.saveAndFlush(
                DeliveryEvaluation.builder()
                        .deliveryEvent(event)
                        .decision(EvaluationDecision.REVIEW_REQUIRED)
                        .reasonCodes(
                                Set.of(DecisionReasonCode.INSUFFICIENT_EVIDENCE)
                        )
                        .recoveryAction(RecoveryAction.MANUAL_INVESTIGATION)
                        .riskScore(0)
                        .decisionExplanation("Additional evidence is required.")
                        .build()
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> processingService.getEvaluation(
                        companyA.getId(),
                        event.getId()
                )
        );
    }
    @Test
    void reviewHistoryRejectsAnotherCompanyEvent() {
        DeliveryCompany companyA = createCompany("Company A");
        DeliveryCompany companyB = createCompany("Company B");

        DeliveryEvent event = createEvent(
                companyB,
                EvaluationStatus.SUSPICIOUS
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> reviewService.getReviewHistory(
                        companyA.getId(),
                        event.getId()
                )
        );
    }
}