package com.example.routeguard.service;

import com.example.routeguard.entity.CompanyApiCredential;
import com.example.routeguard.exception.ResourceNotFoundException;
import com.example.routeguard.repository.CompanyApiCredentialRepository;
import com.example.routeguard.repository.DeliveryCompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiCredentialRevocationServiceTest {

    @Mock
    private CompanyApiCredentialRepository credentialRepository;

    @Mock
    private DeliveryCompanyRepository companyRepository;

    private ApiCredentialService credentialService;

    @BeforeEach
    void setUp() {
        credentialService = new ApiCredentialService(
                credentialRepository,
                companyRepository
        );
    }

    @Test
    void activeCredentialIsRevoked() {
        UUID companyId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();

        CompanyApiCredential credential =
                CompanyApiCredential.builder()
                        .id(credentialId)
                        .active(true)
                        .build();

        when(credentialRepository.findByIdAndCompany_Id(
                credentialId,
                companyId
        )).thenReturn(Optional.of(credential));

        credentialService.revokeCredential(
                companyId,
                credentialId
        );

        assertFalse(credential.isActive());

        verify(credentialRepository).findByIdAndCompany_Id(
                credentialId,
                companyId
        );

        verify(credentialRepository).save(credential);
        verifyNoInteractions(companyRepository);
    }

    @Test
    void alreadyRevokedCredentialRemainsInactive() {
        UUID companyId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();

        CompanyApiCredential credential =
                CompanyApiCredential.builder()
                        .id(credentialId)
                        .active(false)
                        .build();

        when(credentialRepository.findByIdAndCompany_Id(
                credentialId,
                companyId
        )).thenReturn(Optional.of(credential));

        assertDoesNotThrow(
                () -> credentialService.revokeCredential(
                        companyId,
                        credentialId
                )
        );

        assertFalse(credential.isActive());

        verify(credentialRepository, never())
                .save(any(CompanyApiCredential.class));

        verifyNoInteractions(companyRepository);
    }

    @Test
    void unavailableCredentialCannotBeRevoked() {
        UUID companyId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();

        when(credentialRepository.findByIdAndCompany_Id(
                credentialId,
                companyId
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> credentialService.revokeCredential(
                        companyId,
                        credentialId
                )
        );

        assertEquals(
                "API credential not found with ID: " + credentialId,
                exception.getMessage()
        );

        verify(credentialRepository, never())
                .save(any(CompanyApiCredential.class));

        verifyNoInteractions(companyRepository);
    }
}