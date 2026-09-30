package com.example.routeguard.repository;

import com.example.routeguard.entity.PlatformUser;
import com.example.routeguard.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository
        extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByEmail(String email);

    boolean existsByEmail(String email);
    boolean existsByRole(UserRole role);

}