package com.mxis.server.care.repository;

import com.mxis.server.care.entity.CareAlgorithm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareAlgorithmRepository extends JpaRepository<CareAlgorithm, Long> {

    /** DB 유니크 제약(active_flag)상 활성 알고리즘은 전역에서 최대 1개다. */
    Optional<CareAlgorithm> findByIsActiveTrue();
}
