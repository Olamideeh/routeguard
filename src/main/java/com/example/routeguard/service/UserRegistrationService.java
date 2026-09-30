package com.example.routeguard.service;

import com.example.routeguard.dto.RegisterCompanyAdminRequest;
import com.example.routeguard.dto.UserResponse;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.entity.PlatformUser;
import com.example.routeguard.enums.UserRole;
import com.example.routeguard.exception.ResourceAlreadyExistsException;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import com.example.routeguard.repository.PlatformUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.routeguard.dto.BootstrapPlatformAdminRequest;
import com.example.routeguard.dto.CreateCompanyUserRequest;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.exception.BusinessRuleException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final PlatformUserRepository userRepository;
    private final DeliveryCompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse registerCompanyAdmin(
            RegisterCompanyAdminRequest request
    ) {
        String normalizedEmail =
                request.email().trim().toLowerCase();

        String normalizedCompanyCode =
                request.companyCode().trim().toUpperCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResourceAlreadyExistsException(
                    "Email address already exists: "
                            + normalizedEmail
            );
        }

        DeliveryCompany company = companyRepository
                .findByCompanyCode(normalizedCompanyCode)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Company not found with code: "
                                        + normalizedCompanyCode
                        )
                );

        PlatformUser companyAdmin = PlatformUser.builder()
                .fullName(request.fullName())
                .email(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(request.password())
                )
                .role(UserRole.COMPANY_ADMIN)
                .company(company)
                .active(true)
                .build();

        PlatformUser savedUser =
                userRepository.save(companyAdmin);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getCompany().getId(),
                savedUser.isActive(),
                savedUser.getCreatedAt()
        );
    }
    @Transactional
    public UserResponse bootstrapPlatformAdmin(
            BootstrapPlatformAdminRequest request
    ) {
        if (userRepository.existsByRole(
                UserRole.PLATFORM_ADMIN
        )) {
            throw new ResourceAlreadyExistsException(
                    "The platform administrator has already been created"
            );
        }

        String normalizedEmail =
                request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResourceAlreadyExistsException(
                    "Email address already exists: "
                            + normalizedEmail
            );
        }

        PlatformUser platformAdmin = PlatformUser.builder()
                .fullName(request.fullName())
                .email(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(request.password())
                )
                .role(UserRole.PLATFORM_ADMIN)
                .company(null)
                .active(true)
                .build();

        PlatformUser savedUser =
                userRepository.save(platformAdmin);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                null,
                savedUser.isActive(),
                savedUser.getCreatedAt()
        );
    }
    @Transactional
    public UserResponse createCompanyUser(
            UUID companyId,
            CreateCompanyUserRequest request
    ) {
        if (request.role() != UserRole.OPERATIONS_OFFICER
                && request.role() != UserRole.RISK_REVIEWER) {
            throw new BusinessRuleException(
                    "Company administrators can create only "
                            + "OPERATIONS_OFFICER or RISK_REVIEWER users"
            );
        }

        DeliveryCompany company = companyRepository
                .findById(companyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Company not found with ID: "
                                        + companyId
                        )
                );

        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "Company must be active before creating users"
            );
        }

        String normalizedEmail =
                request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResourceAlreadyExistsException(
                    "Email address already exists: "
                            + normalizedEmail
            );
        }

        PlatformUser user = PlatformUser.builder()
                .fullName(request.fullName())
                .email(normalizedEmail)
                .passwordHash(
                        passwordEncoder.encode(request.password())
                )
                .role(request.role())
                .company(company)
                .active(true)
                .build();

        PlatformUser savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getCompany().getId(),
                savedUser.isActive(),
                savedUser.getCreatedAt()
        );
    }
}