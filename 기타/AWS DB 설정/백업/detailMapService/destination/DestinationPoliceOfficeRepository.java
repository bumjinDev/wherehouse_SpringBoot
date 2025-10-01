package com.aws.database.detailMapService.destination;

import com.aws.database.detailMapService.domain.PoliceOffice;


@Repository
public interface DestinationPoliceOfficeRepository extends JpaRepository<PoliceOffice, String> {
}