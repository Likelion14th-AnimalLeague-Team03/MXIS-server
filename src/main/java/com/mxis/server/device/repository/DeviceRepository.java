package com.mxis.server.device.repository;

import com.mxis.server.device.entity.Device;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    @Query("SELECT d FROM Device d WHERE d.id = :id AND d.deletedAt IS NULL")
    Optional<Device> findActiveById(@Param("id") Long id);

    @Query("SELECT d FROM Device d WHERE d.user.id = :userId AND d.deletedAt IS NULL ORDER BY d.registeredAt DESC")
    List<Device> findAllActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT d FROM Device d WHERE d.serialNumber = :serialNumber AND d.deletedAt IS NULL")
    Optional<Device> findActiveBySerialNumber(@Param("serialNumber") String serialNumber);

    boolean existsBySerialNumberAndDeletedAtIsNull(String serialNumber);

    Optional<Device> findBySerialNumber(String serialNumber);
}
