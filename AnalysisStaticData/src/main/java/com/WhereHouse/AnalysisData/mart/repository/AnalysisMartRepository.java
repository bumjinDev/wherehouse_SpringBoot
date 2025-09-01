package com.WhereHouse.AnalysisData.mart.repository;

import com.WhereHouse.AnalysisData.mart.entity.AnalysisMartStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisMartRepository extends JpaRepository<AnalysisMartStatistics, Long> {

    List<AnalysisMartStatistics> findByBusinessStatusName(String businessStatusName);

    List<AnalysisMartStatistics> findByBusinessTypeName(String businessTypeName);

    Optional<AnalysisMartStatistics> findByManagementNo(String managementNo);

    boolean existsByManagementNo(String managementNo);

    @Query("SELECT COUNT(a) FROM AnalysisMartStatistics a")
    long countAnalysisData();

    @Query("SELECT a.businessStatusName, COUNT(a) FROM AnalysisMartStatistics a GROUP BY a.businessStatusName ORDER BY COUNT(a) DESC")
    List<Object[]> findMartCountByBusinessStatus();

    @Query("SELECT a FROM AnalysisMartStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisMartStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisMartStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.businessTypeName, COUNT(a) FROM AnalysisMartStatistics a GROUP BY a.businessTypeName ORDER BY COUNT(a) DESC")
    List<Object[]> findMartCountByBusinessType();

    @Query("SELECT a FROM AnalysisMartStatistics a WHERE a.businessName LIKE %:name%")
    List<AnalysisMartStatistics> findByBusinessNameContaining(@Param("name") String name);
}