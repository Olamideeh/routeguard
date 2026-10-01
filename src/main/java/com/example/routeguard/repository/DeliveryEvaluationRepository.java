package com.example.routeguard.repository;

import com.example.routeguard.entity.DeliveryEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryEvaluationRepository
        extends JpaRepository<DeliveryEvaluation, UUID> {

    Optional<DeliveryEvaluation>
    findByDeliveryEvent_Id(UUID deliveryEventId);
    Optional<DeliveryEvaluation>
    findByDeliveryEvent_IdAndDeliveryEvent_Company_Id(
            UUID eventId,
            UUID companyId
    );
}