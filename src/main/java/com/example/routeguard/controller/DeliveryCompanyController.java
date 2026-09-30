package com.example.routeguard.controller;

import com.example.routeguard.dto.CompanyResponse;
import com.example.routeguard.dto.RegisterCompanyRequest;
import com.example.routeguard.service.DeliveryCompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class DeliveryCompanyController {

    private final DeliveryCompanyService companyService;

    @PostMapping
    public ResponseEntity<CompanyResponse> registerCompany(
            @Valid @RequestBody RegisterCompanyRequest request
    ) {
        CompanyResponse response =
                companyService.registerCompany(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @PatchMapping("/{companyId}/activate")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<CompanyResponse> activateCompany(
            @PathVariable UUID companyId
    ) {
        CompanyResponse response =
                companyService.activateCompany(companyId);

        return ResponseEntity.ok(response);
    }
}