package com.aws.database.detailMapService.source;

import com.aws.database.detailMapService.domain.ArrestRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceArrestRateRepository extends JpaRepository<ArrestRate, String> {
}