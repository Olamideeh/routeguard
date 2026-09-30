package com.example.routeguard.controller;

import com.example.routeguard.dto.AuthenticationResponse;
import com.example.routeguard.dto.LoginRequest;
import com.example.routeguard.dto.RegisterCompanyAdminRequest;
import com.example.routeguard.dto.UserResponse;
import com.example.routeguard.service.AuthenticationService;
import com.example.routeguard.service.UserRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final UserRegistrationService registrationService;
    private final AuthenticationService authenticationService;

    @PostMapping("/company-admin/register")
    public ResponseEntity<UserResponse> registerCompanyAdmin(
            @Valid @RequestBody
            RegisterCompanyAdminRequest request
    ) {
        UserResponse response =
                registrationService.registerCompanyAdmin(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthenticationResponse response =
                authenticationService.login(request);

        return ResponseEntity.ok(response);
    }
}