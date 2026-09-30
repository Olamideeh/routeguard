package com.example.routeguard.entity;

import com.example.routeguard.enums.DecisionReasonCode;
import com.example.routeguard.enums.EvaluationDecision;
import com.example.routeguard.enums.RecoveryAction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_evaluations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_evaluation_delivery_event",
                        columnNames = "delivery_event_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "delivery_event_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_evaluation_delivery_event"
            )
    )
    private DeliveryEvent deliveryEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvaluationDecision decision;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "evaluation_reason_codes",
            joinColumns = @JoinColumn(
                    name = "evaluation_id"
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason_code",
            nullable = false,
            length = 50
    )
    @Builder.Default
    private Set<DecisionReasonCode> reasonCodes =
            new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(
            name = "recovery_action",
            nullable = false,
            length = 40
    )
    private RecoveryAction recoveryAction;

    @Column(
            name = "gps_distance_metres",
            precision = 12,
            scale = 2
    )
    private BigDecimal gpsDistanceMetres;

    @Column(name = "photo_reused")
    private Boolean photoReused;

    @Column(
            name = "risk_score",
            nullable = false
    )
    private int riskScore;

    @Column(
            name = "decision_explanation",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String decisionExplanation;

    @Column(nullable = false, updatable = false)
    private Instant evaluatedAt;

    @PrePersist
    public void beforeInsert() {
        if (evaluatedAt == null) {
            evaluatedAt = Instant.now();
        }

        if (reasonCodes == null) {
            reasonCodes = new HashSet<>();
        }
    }
}