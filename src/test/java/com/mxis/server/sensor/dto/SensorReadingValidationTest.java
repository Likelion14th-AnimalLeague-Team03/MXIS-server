package com.mxis.server.sensor.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SensorReadingValidationTest {
    @Test
    void rejectsPhysicallyImpossibleMeasurementsButAllowsMissingValues() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (SensorReadingItem item : List.of(
                    reading("-273.16", "50", "0"), reading("20", "-0.01", "0"),
                    reading("20", "100.01", "0"), reading("20", "50", "-0.001"))) {
                assertThat(validator.validate(item)).isNotEmpty();
            }
            assertThat(validator.validate(reading("-273.15", "0", "0"))).isEmpty();
            assertThat(validator.validate(reading("20", "100", "0"))).isEmpty();
            assertThat(validator.validate(reading(null, null, null))).isEmpty();
        }
    }

    private SensorReadingItem reading(String temperature, String humidity, String shock) {
        return new SensorReadingItem(1L, decimal(temperature), decimal(humidity), decimal(shock),
                null, false, LocalDateTime.of(2026, 9, 14, 12, 0));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
