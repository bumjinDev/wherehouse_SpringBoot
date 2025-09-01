package com.WhereHouse.AnalysisData.cinema.repository;

import com.WhereHouse.AnalysisData.cinema.entity.AnalysisCinemaStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisCinemaRepository extends JpaRepository<AnalysisCinemaStatistics, Long> {

    List<AnalysisCinemaStatistics> findByBusinessStatusName(String businessStatusName);

    List<AnalysisCinemaStatistics> findByCultureSportsTypeName(String cultureSportsTypeName);

    boolean existsByBusinessName(String businessName);

    @Query("SELECT COUNT(a) FROM AnalysisCinemaStatistics a")
    long countAnalysisData();

    @Query("SELECT a.businessStatusName, COUNT(a) FROM AnalysisCinemaStatistics a GROUP BY a.businessStatusName ORDER BY COUNT(a) DESC")
    List<Object[]> findCinemaCountByBusinessStatus();

    @Query("SELECT a FROM AnalysisCinemaStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisCinemaStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisCinemaStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.cultureSportsTypeName, COUNT(a) FROM AnalysisCinemaStatistics a GROUP BY a.cultureSportsTypeName ORDER BY COUNT(a) DESC")
    List<Object[]> findCinemaCountByCultureSportsType();

    @Query("SELECT a FROM AnalysisCinemaStatistics a WHERE a.businessName LIKE %:name%")
    List<AnalysisCinemaStatistics> findByBusinessNameContaining(@Param("name") String name);
}