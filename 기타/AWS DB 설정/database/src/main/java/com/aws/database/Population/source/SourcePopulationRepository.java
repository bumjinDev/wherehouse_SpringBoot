package com.aws.database.Population.source;

import com.aws.database.Population.domain.AnalysisPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourcePopulationRepository extends JpaRepository<AnalysisPopulationDensity, Long> {
}