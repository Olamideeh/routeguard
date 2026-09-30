package com.example.routeguard.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "company_api_credentials",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_api_credential_key_id",
                        columnNames = "key_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_api_credential_company",
                        columnList = "company_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyApiCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "key_id",
            nullable = false,
            updatable = false
    )
    private UUID keyId;

    @Column(
            name = "api_key_hash",
            nullable = false,
            length = 64
    )
    private String apiKeyHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "company_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_api_credential_company"
            )
    )
    private DeliveryCompany company;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant expiresAt;

    private Instant lastUsedAt;

    @Version
    private Long version;

    @PrePersist
    public void beforeInsert() {
        if (keyId == null) {
            keyId = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}