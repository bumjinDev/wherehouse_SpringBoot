package com.aws.database.Population.destination;


import com.aws.database.Population.domain.AnalysisPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationPopulationRepository extends JpaRepository<AnalysisPopulationDensity, Long> {
}