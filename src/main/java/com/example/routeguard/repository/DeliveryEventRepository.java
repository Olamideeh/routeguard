package com.example.routeguard.repository;

import com.example.routeguard.entity.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

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
}