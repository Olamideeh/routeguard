package com.example.routeguard.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class GpsDistanceService {

    private static final double EARTH_RADIUS_METRES =
            6_371_000.0;

    public BigDecimal calculateDistanceMetres(
            BigDecimal deliveryLatitude,
            BigDecimal deliveryLongitude,
            BigDecimal expectedLatitude,
            BigDecimal expectedLongitude
    ) {
        validateCoordinate(deliveryLatitude, 90, "Delivery latitude");
        validateCoordinate(deliveryLongitude, 180, "Delivery longitude");
        validateCoordinate(expectedLatitude, 90, "Expected latitude");
        validateCoordinate(expectedLongitude, 180, "Expected longitude");

        double latitude1 =
                Math.toRadians(deliveryLatitude.doubleValue());

        double latitude2 =
                Math.toRadians(expectedLatitude.doubleValue());

        double latitudeDifference = latitude2 - latitude1;

        double longitudeDifference = Math.toRadians(
                expectedLongitude.doubleValue()
                        - deliveryLongitude.doubleValue()
        );

        double latitudeSine =
                Math.sin(latitudeDifference / 2.0);

        double longitudeSine =
                Math.sin(longitudeDifference / 2.0);

        double a = latitudeSine * latitudeSine
                + Math.cos(latitude1)
                * Math.cos(latitude2)
                * longitudeSine
                * longitudeSine;

        // Protect against floating-point rounding outside [0, 1].
        a = Math.max(0.0, Math.min(1.0, a));

        double centralAngle = 2.0 * Math.atan2(
                Math.sqrt(a),
                Math.sqrt(1.0 - a)
        );

        double distanceMetres =
                EARTH_RADIUS_METRES * centralAngle;

        return BigDecimal.valueOf(distanceMetres)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validateCoordinate(
            BigDecimal coordinate,
            int limit,
            String fieldName
    ) {
        if (coordinate == null) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }

        BigDecimal maximum = BigDecimal.valueOf(limit);

        if (coordinate.compareTo(maximum.negate()) < 0
                || coordinate.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between "
                            + (-limit) + " and " + limit
            );
        }
    }
}