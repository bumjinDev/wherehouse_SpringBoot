package com.aws.database.detailMapService.source;

import com.aws.database.detailMapService.domain.PoliceOffice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourcePoliceOfficeRepository extends JpaRepository<PoliceOffice, String> {
}