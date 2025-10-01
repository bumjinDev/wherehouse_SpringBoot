package com.aws.database.detailMapService.source;

import com.aws.database.detailMapService.domain.AnalysisPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourcePopulationRepository extends JpaRepository<AnalysisPopulationDensity, Long> {
}