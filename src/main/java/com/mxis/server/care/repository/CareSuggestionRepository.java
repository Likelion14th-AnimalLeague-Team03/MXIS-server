package com.mxis.server.care.repository;

import com.mxis.server.care.entity.CareSuggestion;
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
            ORDER BY cs.createdAt DESC
            """)
    List<CareSuggestion> findActiveByProductId(@Param("productId") Long productId);

    default Optional<CareSuggestion> findLatestActiveByProductId(Long productId) {
        return findActiveByProductId(productId).stream().findFirst();
    }
}
