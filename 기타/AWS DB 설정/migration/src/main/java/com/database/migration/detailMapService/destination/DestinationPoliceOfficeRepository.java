package com.database.migration.detailMapService.destination;

import com.database.migration.detailMapService.domain.PoliceOffice;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface DestinationPoliceOfficeRepository extends JpaRepository<PoliceOffice, String> {
}