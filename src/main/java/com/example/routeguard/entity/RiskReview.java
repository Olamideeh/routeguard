package com.example.routeguard.entity;

import com.example.routeguard.enums.RecoveryAction;
import com.example.routeguard.enums.ReviewDecision;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "risk_reviews",
        indexes = {
                @Index(
                        name = "idx_risk_review_event",
                        columnList = "delivery_event_id, reviewed_at"
                ),
                @Index(
                        name = "idx_risk_review_reviewer",
                        columnList = "reviewer_id"
                )
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "delivery_event_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_risk_review_event"
            )
    )
    private DeliveryEvent deliveryEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reviewer_id",
            nullable = false,
            updatable = false,
            foreignKey = @ForeignKey(
                    name = "fk_risk_review_reviewer"
            )
    )
    private PlatformUser reviewer;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            updatable = false,
            length = 30
    )
    private ReviewDecision decision;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "recovery_action",
            nullable = false,
            updatable = false,
            length = 40
    )
    private RecoveryAction recoveryAction;

    @Column(
            nullable = false,
            updatable = false,
            length = 2000
    )
    private String notes;

    @Column(
            name = "reviewed_at",
            nullable = false,
            updatable = false
    )
    private Instant reviewedAt;

    @PrePersist
    public void beforeInsert() {
        if (reviewedAt == null) {
            reviewedAt = Instant.now();
        }

        if (notes != null) {
            notes = notes.trim();
        }
    }
}