package com.database.migration.detailMapService.source;

import com.database.migration.detailMapService.domain.ArrestRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceArrestRateRepository extends JpaRepository<ArrestRate, String> {
}