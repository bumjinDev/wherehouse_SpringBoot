package com.WhereHouse.AnalysisData.entertainment.repository;

import com.WhereHouse.AnalysisData.entertainment.entity.AnalysisEntertainmentStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisEntertainmentRepository extends JpaRepository<AnalysisEntertainmentStatistics, Long> {

    List<AnalysisEntertainmentStatistics> findByBusinessStatusName(String businessStatusName);

    List<AnalysisEntertainmentStatistics> findByBusinessCategory(String businessCategory);

    List<AnalysisEntertainmentStatistics> findByHygieneBusinessType(String hygieneBusinessType);

    boolean existsByBusinessName(String businessName);

    @Query("SELECT COUNT(a) FROM AnalysisEntertainmentStatistics a")
    long countAnalysisData();

    @Query("SELECT a.businessStatusName, COUNT(a) FROM AnalysisEntertainmentStatistics a GROUP BY a.businessStatusName ORDER BY COUNT(a) DESC")
    List<Object[]> findEntertainmentCountByBusinessStatus();

    @Query("SELECT a FROM AnalysisEntertainmentStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisEntertainmentStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisEntertainmentStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.businessCategory, COUNT(a) FROM AnalysisEntertainmentStatistics a GROUP BY a.businessCategory ORDER BY COUNT(a) DESC")
    List<Object[]> findEntertainmentCountByBusinessCategory();

    @Query("SELECT a FROM AnalysisEntertainmentStatistics a WHERE a.businessName LIKE %:name%")
    List<AnalysisEntertainmentStatistics> findByBusinessNameContaining(@Param("name") String name);
}