package com.example.routeguard.entity;

import com.example.routeguard.enums.CompanyStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "delivery_companies",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_delivery_company_code",
                        columnNames = "company_code"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(
            name = "company_code",
            nullable = false,
            length = 50
    )
    private String companyCode;

    @Column(
            name = "country_code",
            nullable = false,
            length = 2
    )
    private String countryCode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.PENDING;

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
            status = CompanyStatus.PENDING;
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
        if (companyCode != null) {
            companyCode = companyCode.trim().toUpperCase();
        }

        if (countryCode != null) {
            countryCode = countryCode.trim().toUpperCase();
        }

        if (name != null) {
            name = name.trim();
        }
    }
}