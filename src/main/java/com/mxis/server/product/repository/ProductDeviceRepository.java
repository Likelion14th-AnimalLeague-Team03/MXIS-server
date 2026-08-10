package com.mxis.server.product.repository;

import com.mxis.server.product.entity.ProductDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductDeviceRepository extends JpaRepository<ProductDevice, Long> {

    @Query("""
            SELECT pd FROM ProductDevice pd
            WHERE pd.product.id = :productId AND pd.detachedAt IS NULL
            ORDER BY pd.attachedAt DESC
            """)
    List<ProductDevice> findActiveByProductId(@Param("productId") Long productId);

    @Query("""
            SELECT pd FROM ProductDevice pd
            WHERE pd.product.id = :productId AND pd.device.id = :deviceId AND pd.detachedAt IS NULL
            """)
    Optional<ProductDevice> findActiveByProductIdAndDeviceId(
            @Param("productId") Long productId, @Param("deviceId") Long deviceId);

    @Query("""
            SELECT pd FROM ProductDevice pd
            WHERE pd.product.id = :productId AND pd.role = com.mxis.server.common.enums.ProductDeviceRole.PRIMARY_SENSOR
              AND pd.detachedAt IS NULL
            """)
    Optional<ProductDevice> findActivePrimaryByProductId(@Param("productId") Long productId);

    @Query("SELECT pd FROM ProductDevice pd WHERE pd.device.id = :deviceId AND pd.detachedAt IS NULL")
    List<ProductDevice> findActiveByDeviceId(@Param("deviceId") Long deviceId);
}
