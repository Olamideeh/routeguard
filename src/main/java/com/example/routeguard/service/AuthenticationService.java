package com.example.routeguard.service;

import com.example.routeguard.dto.AuthenticationResponse;
import com.example.routeguard.dto.LoginRequest;
import com.example.routeguard.entity.PlatformUser;
import com.example.routeguard.repository.PlatformUserRepository;
import com.example.routeguard.security.GeneratedJwtToken;
import com.example.routeguard.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final PlatformUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Transactional(readOnly = true)
    public AuthenticationResponse login(
            LoginRequest request
    ) {
        String normalizedEmail =
                request.email().trim().toLowerCase();

        PlatformUser user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "Invalid email or password"
                        )
                );

        if (!user.isActive()
                || !passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new BadCredentialsException(
                    "Invalid email or password"
            );
        }

        GeneratedJwtToken generatedToken =
                jwtTokenService.generateToken(user);

        UUID companyId = user.getCompany() == null
                ? null
                : user.getCompany().getId();

        return new AuthenticationResponse(
                generatedToken.value(),
                "Bearer",
                generatedToken.expiresAt(),
                user.getId(),
                user.getEmail(),
                user.getRole(),
                companyId
        );
    }
}