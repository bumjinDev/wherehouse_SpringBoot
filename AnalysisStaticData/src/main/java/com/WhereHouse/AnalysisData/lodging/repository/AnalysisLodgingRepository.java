package com.WhereHouse.AnalysisData.lodging.repository;

import com.WhereHouse.AnalysisData.lodging.entity.AnalysisLodgingStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisLodgingRepository extends JpaRepository<AnalysisLodgingStatistics, Long> {

    List<AnalysisLodgingStatistics> findByBusinessStatusName(String businessStatusName);

    List<AnalysisLodgingStatistics> findByBusinessTypeName(String businessTypeName);

    List<AnalysisLodgingStatistics> findByHygieneBusinessType(String hygieneBusinessType);

    @Query("SELECT COUNT(a) FROM AnalysisLodgingStatistics a")
    long countAnalysisData();

    @Query("SELECT a.businessStatusName, COUNT(a) FROM AnalysisLodgingStatistics a GROUP BY a.businessStatusName ORDER BY COUNT(a) DESC")
    List<Object[]> findLodgingCountByBusinessStatus();

    @Query("SELECT a FROM AnalysisLodgingStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisLodgingStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisLodgingStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.businessTypeName, COUNT(a) FROM AnalysisLodgingStatistics a GROUP BY a.businessTypeName ORDER BY COUNT(a) DESC")
    List<Object[]> findLodgingCountByBusinessType();

    @Query("SELECT a FROM AnalysisLodgingStatistics a WHERE a.businessName LIKE %:name%")
    List<AnalysisLodgingStatistics> findByBusinessNameContaining(@Param("name") String name);
}