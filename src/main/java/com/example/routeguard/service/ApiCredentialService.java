package com.example.routeguard.service;

import com.example.routeguard.dto.ApiCredentialResponse;
import com.example.routeguard.entity.CompanyApiCredential;
import com.example.routeguard.entity.DeliveryCompany;
import com.example.routeguard.enums.CompanyStatus;
import com.example.routeguard.exception.BusinessRuleException;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.repository.CompanyApiCredentialRepository;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiCredentialService {

    private static final String KEY_PREFIX = "rg_live";

    private final CompanyApiCredentialRepository credentialRepository;
    private final DeliveryCompanyRepository companyRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ApiCredentialResponse createCredential(
            UUID companyId
    ) {
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
                    "Company must be active before creating API credentials"
            );
        }

        UUID keyId = UUID.randomUUID();

        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);

        String secret = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(secretBytes);

        String completeApiKey =
                KEY_PREFIX + "." + keyId + "." + secret;

        CompanyApiCredential credential =
                CompanyApiCredential.builder()
                        .keyId(keyId)
                        .apiKeyHash(hashApiKey(completeApiKey))
                        .company(company)
                        .active(true)
                        .build();

        CompanyApiCredential savedCredential =
                credentialRepository.save(credential);

        return new ApiCredentialResponse(
                savedCredential.getId(),
                savedCredential.getKeyId(),
                completeApiKey,
                savedCredential.getCreatedAt(),
                savedCredential.getExpiresAt()
        );
    }

    @Transactional
    public DeliveryCompany authenticateApiKey(
            String apiKey
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BadCredentialsException(
                    "API key is required"
            );
        }

        String[] parts = apiKey.trim().split("\\.", 3);

        if (parts.length != 3
                || !KEY_PREFIX.equals(parts[0])) {
            throw new BadCredentialsException(
                    "Invalid API key"
            );
        }

        UUID keyId;

        try {
            keyId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(
                    "Invalid API key"
            );
        }

        CompanyApiCredential credential =
                credentialRepository.findByKeyId(keyId)
                        .orElseThrow(() ->
                                new BadCredentialsException(
                                        "Invalid API key"
                                )
                        );

        String suppliedHash = hashApiKey(apiKey.trim());

        boolean hashMatches = MessageDigest.isEqual(
                credential.getApiKeyHash()
                        .getBytes(StandardCharsets.UTF_8),
                suppliedHash.getBytes(StandardCharsets.UTF_8)
        );

        boolean expired =
                credential.getExpiresAt() != null
                        && credential.getExpiresAt()
                        .isBefore(Instant.now());

        if (!credential.isActive()
                || expired
                || !hashMatches
                || credential.getCompany().getStatus()
                != CompanyStatus.ACTIVE) {
            throw new BadCredentialsException(
                    "Invalid API key"
            );
        }

        credential.setLastUsedAt(Instant.now());
        credentialRepository.save(credential);

        return credential.getCompany();
    }

    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    apiKey.getBytes(StandardCharsets.UTF_8)
            );

            return convertToHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    private String convertToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();

        for (byte value : bytes) {
            hex.append(
                    String.format("%02x", value & 0xff)
            );
        }

        return hex.toString();
    }
}