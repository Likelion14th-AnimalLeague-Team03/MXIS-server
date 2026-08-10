package com.mxis.server.sensor.repository;

import com.mxis.server.sensor.entity.SensorReading;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    @Query("""
            SELECT sr.sequenceNumber FROM SensorReading sr
            WHERE sr.device.id = :deviceId AND sr.sequenceNumber IN :sequenceNumbers
            """)
    List<Long> findExistingSequenceNumbers(
            @Param("deviceId") Long deviceId, @Param("sequenceNumbers") List<Long> sequenceNumbers);
}
