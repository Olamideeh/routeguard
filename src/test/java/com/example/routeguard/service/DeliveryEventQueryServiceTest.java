package com.example.routeguard.service;

import com.example.routeguard.dto.DeliveryEventPageResponse;
import com.example.routeguard.entity.DeliveryEvent;
import com.example.routeguard.enums.DeliveryEventType;
import com.example.routeguard.enums.EvaluationStatus;
import com.example.routeguard.repository.DeliveryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.example.routeguard.exception.BusinessRuleException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryEventQueryServiceTest {

    @Mock
    private DeliveryEventRepository eventRepository;

    private DeliveryEventQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new DeliveryEventQueryService(
                eventRepository
        );
    }

    @Test
    void returnsEventsWithCompanyFilterAndPagination() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Instant timestamp =
                Instant.parse("2026-09-30T20:00:00Z");

        DeliveryEvent event = DeliveryEvent.builder()
                .id(eventId)
                .reference("EVT-LIST-001")
                .externalDeliveryId("DEL-1001")
                .eventType(DeliveryEventType.DELIVERED)
                .status(EvaluationStatus.SUSPICIOUS)
                .eventTimestamp(timestamp)
                .createdAt(timestamp)
                .build();

        PageRequest expectedPageRequest = PageRequest.of(
                0,
                10,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        when(eventRepository.findByCompany_IdAndStatus(
                companyId,
                EvaluationStatus.SUSPICIOUS,
                expectedPageRequest
        )).thenReturn(
                new PageImpl<>(
                        List.of(event),
                        expectedPageRequest,
                        21
                )
        );

        DeliveryEventPageResponse response =
                queryService.getEvents(
                        companyId,
                        EvaluationStatus.SUSPICIOUS,
                        0,
                        10
                );

        assertAll(
                () -> assertEquals(1, response.content().size()),
                () -> assertEquals(
                        eventId,
                        response.content().get(0).id()
                ),
                () -> assertEquals(
                        "EVT-LIST-001",
                        response.content().get(0).reference()
                ),
                () -> assertEquals(
                        EvaluationStatus.SUSPICIOUS,
                        response.content().get(0).status()
                ),
                () -> assertEquals(0, response.page()),
                () -> assertEquals(10, response.size()),
                () -> assertEquals(21L, response.totalElements()),
                () -> assertEquals(3, response.totalPages()),
                () -> assertFalse(response.last())
        );

        verify(eventRepository).findByCompany_IdAndStatus(
                companyId,
                EvaluationStatus.SUSPICIOUS,
                expectedPageRequest
        );
    }
    @ParameterizedTest
    @CsvSource({
            "-1, 10",
            "0, 0",
            "0, 101"
    })
    void invalidPaginationIsRejected(int page, int size) {
        assertThrows(
                BusinessRuleException.class,
                () -> queryService.getEvents(
                        UUID.randomUUID(),
                        EvaluationStatus.SUSPICIOUS,
                        page,
                        size
                )
        );

        verifyNoInteractions(eventRepository);
    }
}