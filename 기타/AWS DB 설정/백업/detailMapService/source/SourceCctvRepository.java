package com.aws.database.detailMapService.source;

import com.aws.database.detailMapService.domain.Cctv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SourceCctvRepository extends JpaRepository<Cctv, Long> {
}