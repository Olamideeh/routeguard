package com.example.routeguard.controller;

import com.example.routeguard.dto.CreateCompanyUserRequest;
import com.example.routeguard.dto.UserResponse;
import com.example.routeguard.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company-users")
@RequiredArgsConstructor
public class CompanyUserController {

    private final UserRegistrationService registrationService;

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<UserResponse> createCompanyUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            CreateCompanyUserRequest request
    ) {
        UUID companyId = UUID.fromString(
                jwt.getClaimAsString("companyId")
        );

        UserResponse response =
                registrationService.createCompanyUser(
                        companyId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}