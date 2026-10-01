package com.example.routeguard.repository;

import com.example.routeguard.entity.RiskReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RiskReviewRepository
        extends JpaRepository<RiskReview, UUID> {

    List<RiskReview>
    findByDeliveryEvent_IdAndDeliveryEvent_Company_IdOrderByReviewedAtAscIdAsc(
            UUID eventId,
            UUID companyId
    );
}