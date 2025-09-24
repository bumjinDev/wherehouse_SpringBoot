package com.aws.database.CRIME.source;

import com.aws.database.CRIME.domain.AnalysisCrimeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceCrimeRepository extends JpaRepository<AnalysisCrimeStatistics, Long> {
}