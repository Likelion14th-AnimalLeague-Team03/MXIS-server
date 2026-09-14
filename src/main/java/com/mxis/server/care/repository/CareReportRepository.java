package com.mxis.server.care.repository;

import com.mxis.server.care.entity.CareReport;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareReportRepository extends JpaRepository<CareReport, Long> {

    default Optional<CareReport> findFirstByProductIdOrderByCreatedAtDesc(Long productId) {
        return findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(productId, 30);
    }

    Optional<CareReport> findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(
            Long productId, int analysisWindowDays);

    Optional<CareReport> findByIdAndProductId(Long id, Long productId);

    default List<CareReport> findAllByProductIdOrderByCreatedAtDesc(Long productId) {
        return findAllByProductIdAndAnalysisWindowDaysOrderByCreatedAtDesc(productId, 30);
    }

    List<CareReport> findAllByProductIdAndAnalysisWindowDaysOrderByCreatedAtDesc(Long productId, int analysisWindowDays);
}
