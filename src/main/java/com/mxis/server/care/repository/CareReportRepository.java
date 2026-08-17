package com.mxis.server.care.repository;

import com.mxis.server.care.entity.CareReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareReportRepository extends JpaRepository<CareReport, Long> {

    Optional<CareReport> findFirstByProductIdOrderByCreatedAtDesc(Long productId);

    Optional<CareReport> findByIdAndProductId(Long id, Long productId);

    List<CareReport> findAllByProductIdOrderByCreatedAtDesc(Long productId);
}
