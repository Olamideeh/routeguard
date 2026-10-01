package com.example.routeguard.repository;

import com.example.routeguard.entity.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.example.routeguard.enums.EvaluationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryEventRepository
        extends JpaRepository<DeliveryEvent, UUID> {

    Optional<DeliveryEvent>
    findByCompany_IdAndIdempotencyKey(
            UUID companyId,
            String idempotencyKey
    );

    boolean existsByCompany_IdAndProofPhotoHash(
            UUID companyId,
            String proofPhotoHash
    );

    List<DeliveryEvent>
    findByCompany_IdAndExternalDeliveryIdOrderByEventTimestampAsc(
            UUID companyId,
            String externalDeliveryId
    );
    boolean existsByCompany_IdAndProofPhotoHashAndIdNot(
            UUID companyId,
            String proofPhotoHash,
            UUID eventId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DeliveryEvent> findByIdAndCompany_Id(
            UUID eventId,
            UUID companyId
    );
    boolean existsByIdAndCompany_Id(
            UUID eventId,
            UUID companyId
    );
    Page<DeliveryEvent> findByCompany_IdAndStatus(
            UUID companyId,
            EvaluationStatus status,
            Pageable pageable
    );
}