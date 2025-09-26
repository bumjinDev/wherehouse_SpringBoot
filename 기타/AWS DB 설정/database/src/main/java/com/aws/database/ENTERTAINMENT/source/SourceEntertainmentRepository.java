package com.aws.database.ENTERTAINMENT.source;

import com.aws.database.ENTERTAINMENT.domain.AnalysisEntertainmentStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceEntertainmentRepository extends JpaRepository<AnalysisEntertainmentStatistics, Long> {
}