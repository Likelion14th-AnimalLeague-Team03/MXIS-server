package com.mxis.server.care.repository;

import com.mxis.server.care.entity.CareGuide;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareGuideRepository extends JpaRepository<CareGuide, Long> {

    Optional<CareGuide> findFirstByCareTypeAndActiveTrue(String careType);

    Optional<CareGuide> findFirstByMaterialIdAndMaterialSubtypeAndActiveTrue(String materialId, String materialSubtype);

    Optional<CareGuide> findFirstByMaterialIdAndMaterialSubtypeIsNullAndActiveTrue(String materialId);
}
