package com.example.routeguard.entity;

import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_company_idempotency_key",
                        columnNames = {
                                "company_id",
                                "idempotency_key"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_delivery_event_reference",
                        columnNames = "reference"
                )
        },
        indexes = {
                @Index(
                        name = "idx_event_external_delivery",
                        columnList = "company_id, external_delivery_id"
                ),
                @Index(
                        name = "idx_event_status",
                        columnList = "status"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String reference;

    @Column(
            name = "external_delivery_id",
            nullable = false,
            length = 150
    )
    private String externalDeliveryId;

    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 100
    )
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_type",
            nullable = false,
            length = 30
    )
    private DeliveryEventType eventType;

    @Column(
            name = "event_timestamp",
            nullable = false
    )
    private Instant eventTimestamp;

    @Column(
            name = "rider_id",
            nullable = false,
            length = 150
    )
    private String riderId;

    @Column(
            name = "customer_id",
            nullable = false,
            length = 150
    )
    private String customerId;

    @Column(
            name = "delivery_latitude",
            precision = 10,
            scale = 7
    )
    private BigDecimal deliveryLatitude;

    @Column(
            name = "delivery_longitude",
            precision = 10,
            scale = 7
    )
    private BigDecimal deliveryLongitude;

    @Column(
            name = "expected_latitude",
            precision = 10,
            scale = 7
    )
    private BigDecimal expectedLatitude;

    @Column(
            name = "expected_longitude",
            precision = 10,
            scale = 7
    )
    private BigDecimal expectedLongitude;

    @Column(name = "otp_verified")
    private Boolean otpVerified;

    @Column(
            name = "proof_photo_hash",
            length = 64
    )
    private String proofPhotoHash;

    @Column(
            name = "failure_reason",
            length = 500
    )
    private String failureReason;

    @Column(
            name = "customer_contact_attempted"
    )
    private Boolean customerContactAttempted;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EvaluationStatus status =
            EvaluationStatus.RECEIVED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_delivery_event_company"
            )
    )
    private DeliveryCompany company;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void beforeInsert() {
        Instant now = Instant.now();

        if (status == null) {
            status = EvaluationStatus.RECEIVED;
        }

        normalizeValues();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void beforeUpdate() {
        normalizeValues();
        updatedAt = Instant.now();
    }

    private void normalizeValues() {
        if (externalDeliveryId != null) {
            externalDeliveryId =
                    externalDeliveryId.trim();
        }

        if (idempotencyKey != null) {
            idempotencyKey =
                    idempotencyKey.trim();
        }

        if (riderId != null) {
            riderId = riderId.trim();
        }

        if (customerId != null) {
            customerId = customerId.trim();
        }

        if (proofPhotoHash != null) {
            proofPhotoHash =
                    proofPhotoHash.trim().toLowerCase();
        }

        if (failureReason != null) {
            failureReason =
                    failureReason.trim();
        }
    }
}