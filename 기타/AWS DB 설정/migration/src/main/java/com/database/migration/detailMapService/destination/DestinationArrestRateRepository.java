package com.database.migration.detailMapService.destination;

import com.database.migration.detailMapService.domain.ArrestRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationArrestRateRepository extends JpaRepository<ArrestRate, String> {
}