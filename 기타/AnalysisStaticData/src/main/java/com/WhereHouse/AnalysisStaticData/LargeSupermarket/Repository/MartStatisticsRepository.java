package com.WhereHouse.AnalysisStaticData.LargeSupermarket.Repository;

import com.WhereHouse.AnalysisStaticData.LargeSupermarket.Entity.MartStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface MartStatisticsRepository extends JpaRepository<MartStatistics, Long> {
    List<MartStatistics> findByBusinessStatusName(String businessStatusName);
    List<MartStatistics> findByBusinessTypeName(String businessTypeName);
    Optional<MartStatistics> findByManagementNo(String managementNo);
    boolean existsByManagementNo(String managementNo);

    @Query("SELECT m FROM MartStatistics m WHERE m.businessName LIKE %:name%")
    List<MartStatistics> findByBusinessNameContaining(@Param("name") String name);

    @Query("SELECT m FROM MartStatistics m WHERE m.coordX BETWEEN :minX AND :maxX AND m.coordY BETWEEN :minY AND :maxY")
    List<MartStatistics> findByLocationBounds(@Param("minX") Double minX, @Param("maxX") Double maxX,
                                              @Param("minY") Double minY, @Param("maxY") Double maxY);

    @Query("SELECT COUNT(m) FROM MartStatistics m WHERE m.businessStatusName = :statusName")
    Long countByBusinessStatus(@Param("statusName") String statusName);
}