package com.database.migration.detailMapService.source;

import com.database.migration.detailMapService.domain.Cctv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceCctvRepository extends JpaRepository<Cctv, Long> {
}