package com.aws.database.detailMapService.destination;


import com.aws.database.detailMapService.domain.AnalysisPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationPopulationRepository extends JpaRepository<AnalysisPopulationDensity, Long> {
}