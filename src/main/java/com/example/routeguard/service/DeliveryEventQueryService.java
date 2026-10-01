package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventPageResponse;
import com.example.routeguard.dto.DeliveryEventSummaryResponse;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.exception.BusinessRuleException;
import com.example.routeguard.repository.DeliveryEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryEventQueryService {

    private final DeliveryEventRepository eventRepository;

    @Transactional(readOnly = true)
    public DeliveryEventPageResponse getEvents(
            UUID companyId,
            EvaluationStatus status,
            int page,
            int size
    ) {
        if (companyId == null) {
            throw new BusinessRuleException(
                    "Company ID is required"
            );
        }

        if (status == null) {
            throw new BusinessRuleException(
                    "Event status is required"
            );
        }

        if (page < 0) {
            throw new BusinessRuleException(
                    "Page number cannot be negative"
            );
        }

        if (size < 1 || size > 100) {
            throw new BusinessRuleException(
                    "Page size must be between 1 and 100"
            );
        }

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<DeliveryEvent> events = eventRepository
                .findByCompany_IdAndStatus(
                        companyId,
                        status,
                        pageRequest
                );

        return new DeliveryEventPageResponse(
                events.getContent()
                        .stream()
                        .map(this::mapToSummary)
                        .toList(),
                events.getNumber(),
                events.getSize(),
                events.getTotalElements(),
                events.getTotalPages(),
                events.isLast()
        );
    }

    private DeliveryEventSummaryResponse mapToSummary(
            DeliveryEvent event
    ) {
        return new DeliveryEventSummaryResponse(
                event.getId(),
                event.getReference(),
                event.getExternalDeliveryId(),
                event.getEventType(),
                event.getStatus(),
                event.getEventTimestamp(),
                event.getCreatedAt()
        );
    }
}