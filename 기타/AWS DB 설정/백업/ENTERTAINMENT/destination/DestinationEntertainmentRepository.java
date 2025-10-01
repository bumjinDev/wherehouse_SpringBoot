package com.aws.database.ENTERTAINMENT.destination;

import com.aws.database.ENTERTAINMENT.domain.AnalysisEntertainmentStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationEntertainmentRepository extends JpaRepository<AnalysisEntertainmentStatistics, Long> {
}