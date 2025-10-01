package com.aws.database.detailMapService.destination;

import com.aws.database.detailMapService.domain.ArrestRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DestinationArrestRateRepository extends JpaRepository<ArrestRate, String> {
}