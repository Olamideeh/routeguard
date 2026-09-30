package com.example.routeguard.repository;

import com.example.routeguard.entity.DeliveryCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeliveryCompanyRepository
        extends JpaRepository<DeliveryCompany, UUID> {

    Optional<DeliveryCompany> findByCompanyCode(String companyCode);

    boolean existsByCompanyCode(String companyCode);
}