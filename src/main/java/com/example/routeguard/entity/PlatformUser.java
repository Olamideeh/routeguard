package com.example.routeguard.entity;

import com.example.routeguard.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "platform_users",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_platform_user_email",
                        columnNames = "email"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            name = "full_name",
            nullable = false,
            length = 150
    )
    private String fullName;

    @Column(nullable = false, length = 200)
    private String email;

    @Column(
            name = "password_hash",
            nullable = false,
            length = 255
    )
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "company_id",
            foreignKey = @ForeignKey(
                    name = "fk_platform_user_company"
            )
    )
    private DeliveryCompany company;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @PrePersist
    public void beforeInsert() {
        Instant now = Instant.now();

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
        if (fullName != null) {
            fullName = fullName.trim();
        }

        if (email != null) {
            email = email.trim().toLowerCase();
        }
    }
}