package com.WhereHouse.AnalysisData.crime.repository;

import com.WhereHouse.AnalysisData.crime.entity.AnalysisCrimeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisCrimeRepository extends JpaRepository<AnalysisCrimeStatistics, Long> {

    Optional<AnalysisCrimeStatistics> findByDistrictName(String districtName);

    List<AnalysisCrimeStatistics> findAllByOrderByTotalOccurrenceDesc();

    boolean existsByDistrictName(String districtName);

    @Query("SELECT COUNT(a) FROM AnalysisCrimeStatistics a")
    long countAnalysisData();

    @Query("SELECT a.districtName, a.totalOccurrence FROM AnalysisCrimeStatistics a ORDER BY a.totalOccurrence DESC")
    List<Object[]> findDistrictCrimeRanking();
}