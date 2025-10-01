package com.database.migration.detailMapService.destination;

import com.database.migration.detailMapService.domain.Cctv;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.stereotype.Repository;

@Repository
public interface DestinationCctvRepository extends JpaRepository<Cctv, Long> {
}