package com.example.routeguard.controller;

import com.example.routeguard.dto.ApiCredentialResponse;
import com.example.routeguard.service.ApiCredentialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-credentials")
@RequiredArgsConstructor
public class ApiCredentialController {

    private final ApiCredentialService credentialService;

    @PostMapping
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiCredentialResponse>
    createCredential(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID companyId = UUID.fromString(
                jwt.getClaimAsString("companyId")
        );

        ApiCredentialResponse response =
                credentialService.createCredential(companyId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @DeleteMapping("/{credentialId}")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<Void> revokeCredential(
            @PathVariable UUID credentialId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID companyId = UUID.fromString(
                jwt.getClaimAsString("companyId")
        );

        credentialService.revokeCredential(
                companyId,
                credentialId
        );

        return ResponseEntity.noContent().build();
    }
}