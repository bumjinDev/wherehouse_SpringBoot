package com.database.migration.detailMapService.source;

import com.database.migration.detailMapService.domain.PoliceOffice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourcePoliceOfficeRepository extends JpaRepository<PoliceOffice, String> {
}