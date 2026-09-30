package com.example.routeguard.security;

import com.example.routeguard.entity.PlatformUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${security.jwt.expiration-minutes}")
    private long expirationMinutes;

    public GeneratedJwtToken generateToken(
            PlatformUser user
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                expirationMinutes,
                ChronoUnit.MINUTES
        );

        JwtClaimsSet.Builder claimsBuilder =
                JwtClaimsSet.builder()
                        .issuer("routeguard")
                        .issuedAt(issuedAt)
                        .expiresAt(expiresAt)
                        .subject(user.getId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name());

        if (user.getCompany() != null) {
            claimsBuilder.claim(
                    "companyId",
                    user.getCompany().getId().toString()
            );
        }

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String token = jwtEncoder
                .encode(
                        JwtEncoderParameters.from(
                                header,
                                claimsBuilder.build()
                        )
                )
                .getTokenValue();

        return new GeneratedJwtToken(
                token,
                expiresAt
        );
    }
}