package com.example.routeguard.repository;

import com.example.routeguard.entity.CompanyApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyApiCredentialRepository
        extends JpaRepository<CompanyApiCredential, UUID> {

    Optional<CompanyApiCredential> findByKeyId(UUID keyId);
}