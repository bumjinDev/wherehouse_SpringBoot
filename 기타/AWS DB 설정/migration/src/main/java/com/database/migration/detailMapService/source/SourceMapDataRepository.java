package com.database.migration.detailMapService.source;

import com.database.migration.detailMapService.domain.MapData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceMapDataRepository extends JpaRepository<MapData, Double> {
}