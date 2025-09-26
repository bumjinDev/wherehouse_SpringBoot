package com.aws.database.CRIME.destination;

import com.aws.database.CRIME.domain.AnalysisCrimeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationCrimeRepository extends JpaRepository<AnalysisCrimeStatistics, Long> {
}