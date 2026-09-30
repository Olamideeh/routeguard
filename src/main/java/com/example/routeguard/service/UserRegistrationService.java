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
}