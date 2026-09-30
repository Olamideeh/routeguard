package com.example.routeguard.dto;

import com.example.routeguard.enums.DeliveryEventType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record DeliveryEventRequest(

        @NotBlank(message = "External delivery ID is required")
        @Size(max = 150)
        String externalDeliveryId,

        @NotNull(message = "Event type is required")
        DeliveryEventType eventType,

        @NotNull(message = "Event timestamp is required")
        Instant eventTimestamp,

        @NotBlank(message = "Rider ID is required")
        @Size(max = 150)
        String riderId,

        @NotBlank(message = "Customer ID is required")
        @Size(max = 150)
        String customerId,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal deliveryLatitude,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal deliveryLongitude,

        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        BigDecimal expectedLatitude,

        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        BigDecimal expectedLongitude,

        Boolean otpVerified,

        @Pattern(
                regexp = "^[a-fA-F0-9]{64}$",
                message = "Proof photo hash must be a 64-character SHA-256 hexadecimal value"
        )
        String proofPhotoHash,

        @Size(max = 500)
        String failureReason,

        Boolean customerContactAttempted
) {
}