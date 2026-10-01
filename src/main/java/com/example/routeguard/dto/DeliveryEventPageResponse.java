package com.example.routeguard.dto;

import java.util.List;

public record DeliveryEventPageResponse(
        List<DeliveryEventSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public DeliveryEventPageResponse {
        content = List.copyOf(content);
    }
}