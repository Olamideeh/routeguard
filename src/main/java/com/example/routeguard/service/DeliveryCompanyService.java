package com.example.routeguard.service;

import com.example.routeguard.dto.CompanyResponse;
import com.example.routeguard.dto.RegisterCompanyRequest;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.exception.ResourceAlreadyExistsException;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeliveryCompanyService {

    private final DeliveryCompanyRepository companyRepository;

    @Transactional
    public CompanyResponse registerCompany(
            RegisterCompanyRequest request
    ) {
        String normalizedCompanyCode =
                request.companyCode().trim().toUpperCase();

        if (companyRepository.existsByCompanyCode(
                normalizedCompanyCode
        )) {
            throw new ResourceAlreadyExistsException(
                    "Company code already exists: "
                            + normalizedCompanyCode
            );
        }

        DeliveryCompany company = DeliveryCompany.builder()
                .name(request.name())
                .companyCode(normalizedCompanyCode)
                .countryCode(
                        request.countryCode()
                                .trim()
                                .toUpperCase()
                )
                .status(CompanyStatus.PENDING)
                .build();

        DeliveryCompany savedCompany =
                companyRepository.save(company);

        return mapToResponse(savedCompany);
    }

    private CompanyResponse mapToResponse(
            DeliveryCompany company
    ) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getCompanyCode(),
                company.getCountryCode(),
                company.getStatus(),
                company.getCreatedAt()
        );
    }
}