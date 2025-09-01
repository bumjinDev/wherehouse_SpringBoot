package com.WhereHouse.AnalysisData.residentcenter.repository;

import com.WhereHouse.AnalysisData.residentcenter.entity.AnalysisResidentCenterStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisResidentCenterRepository extends JpaRepository<AnalysisResidentCenterStatistics, Long> {

    List<AnalysisResidentCenterStatistics> findBySigungu(String sigungu);

    List<AnalysisResidentCenterStatistics> findByEupmeondong(String eupmeondong);

    @Query("SELECT COUNT(a) FROM AnalysisResidentCenterStatistics a")
    long countAnalysisData();

    @Query("SELECT a.sigungu, COUNT(a) FROM AnalysisResidentCenterStatistics a GROUP BY a.sigungu ORDER BY COUNT(a) DESC")
    List<Object[]> findResidentCenterCountBySigungu();

    @Query("SELECT a FROM AnalysisResidentCenterStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisResidentCenterStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisResidentCenterStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.eupmeondong, COUNT(a) FROM AnalysisResidentCenterStatistics a GROUP BY a.eupmeondong ORDER BY COUNT(a) DESC")
    List<Object[]> findResidentCenterCountByEupmeondong();
}