package com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Repository;

import com.WhereHouse.AnalysisStaticData.CCTVInfoRoad.Entity.CctvStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CctvStatisticsRepository extends JpaRepository<CctvStatistics, Long> {
    Optional<CctvStatistics> findByRoadAddress(String roadAddress);
    List<CctvStatistics> findAllByOrderByCameraCountDesc();
    boolean existsByRoadAddress(String roadAddress);
}