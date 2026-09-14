package com.mxis.server.care.repository;

import com.mxis.server.care.entity.CareSuggestion;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CareSuggestionRepository extends JpaRepository<CareSuggestion, Long> {

    @Query("""
            SELECT cs FROM CareSuggestion cs
            WHERE cs.product.id = :productId
              AND cs.status = com.mxis.server.common.enums.CareSuggestionStatus.ACTIVE
              AND (cs.expiresAt IS NULL OR cs.expiresAt > :now)
            ORDER BY cs.createdAt DESC
            """)
    List<CareSuggestion> findActiveByProductIdAt(@Param("productId") Long productId,
                                              @Param("now") LocalDateTime now);

    default List<CareSuggestion> findActiveByProductId(Long productId) {
        return findActiveByProductIdAt(productId, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    @Query("""
            SELECT cs FROM CareSuggestion cs
            WHERE cs.product.id = :productId
              AND cs.status = com.mxis.server.common.enums.CareSuggestionStatus.ACTIVE
            ORDER BY cs.createdAt DESC
            """)
    List<CareSuggestion> findAllActiveIncludingExpiredByProductId(@Param("productId") Long productId);

    default Optional<CareSuggestion> findLatestActiveByProductId(Long productId) {
        return findLatestActiveByProductId(productId, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
    }

    default Optional<CareSuggestion> findLatestActiveByProductId(Long productId, LocalDateTime now) {
        return findActiveByProductIdAt(productId, now).stream().findFirst();
    }
}
