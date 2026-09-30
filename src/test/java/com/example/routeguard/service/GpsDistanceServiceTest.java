package com.example.routeguard.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpsDistanceServiceTest {

    private final GpsDistanceService service =
            new GpsDistanceService();

    @Test
    void identicalCoordinatesReturnZeroMetres() {
        BigDecimal distance = service.calculateDistanceMetres(
                new BigDecimal("6.4541000"),
                new BigDecimal("3.3947000"),
                new BigDecimal("6.4541000"),
                new BigDecimal("3.3947000")
        );

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(distance)
        );
    }

    @Test
    void oneDegreeOfLongitudeAtEquatorIsAbout111195Metres() {
        BigDecimal distance = service.calculateDistanceMetres(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE
        );

        // Allow one metre of rounding tolerance.
        assertEquals(
                111195.0,
                distance.doubleValue(),
                1.0
        );
    }
    @Test
    void missingCoordinateIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateDistanceMetres(
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void latitudeAboveNinetyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateDistanceMetres(
                        new BigDecimal("90.0000001"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void longitudeBelowMinusOneHundredEightyIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.calculateDistanceMetres(
                        BigDecimal.ZERO,
                        new BigDecimal("-180.0000001"),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void crossingDateLineUsesShortDistance() {
        BigDecimal distance = service.calculateDistanceMetres(
                BigDecimal.ZERO,
                new BigDecimal("179.9"),
                BigDecimal.ZERO,
                new BigDecimal("-179.9")
        );

        // These points are about 22 kilometres apart.
        assertEquals(
                22239.0,
                distance.doubleValue(),
                1.0
        );
    }

    @Test
    void oppositePointsReturnFiniteDistance() {
        BigDecimal distance = service.calculateDistanceMetres(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("180")
        );

        assertTrue(Double.isFinite(distance.doubleValue()));

        assertEquals(
                20015086.8,
                distance.doubleValue(),
                1.0
        );
    }
}