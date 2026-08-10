package com.mxis.server.user.repository;

import com.mxis.server.common.enums.ConsentType;
import com.mxis.server.user.entity.Consent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    List<Consent> findByUserIdOrderByOccurredAtAsc(Long userId);

    @Query("""
            SELECT c FROM Consent c
            WHERE c.user.id = :userId AND c.consentType = :type
            ORDER BY c.occurredAt DESC
            """)
    List<Consent> findLatestFirstByUserIdAndType(@Param("userId") Long userId, @Param("type") ConsentType type);
}
