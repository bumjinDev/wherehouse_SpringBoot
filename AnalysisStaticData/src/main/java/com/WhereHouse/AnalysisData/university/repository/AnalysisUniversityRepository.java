package com.WhereHouse.AnalysisData.university.repository;

import com.WhereHouse.AnalysisData.university.entity.AnalysisUniversityStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisUniversityRepository extends JpaRepository<AnalysisUniversityStatistics, Long> {

    List<AnalysisUniversityStatistics> findBySchoolName(String schoolName);

    List<AnalysisUniversityStatistics> findByUniversityType(String universityType);

    List<AnalysisUniversityStatistics> findBySidoName(String sidoName);

    boolean existsBySchoolNameAndUniversityType(String schoolName, String universityType);

    @Query("SELECT COUNT(a) FROM AnalysisUniversityStatistics a")
    long countAnalysisData();

    @Query("SELECT a.sidoName, COUNT(a) FROM AnalysisUniversityStatistics a GROUP BY a.sidoName ORDER BY COUNT(a) DESC")
    List<Object[]> findUniversityCountBySido();

    @Query("SELECT a FROM AnalysisUniversityStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisUniversityStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisUniversityStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.universityType, COUNT(a) FROM AnalysisUniversityStatistics a GROUP BY a.universityType ORDER BY COUNT(a) DESC")
    List<Object[]> findUniversityCountByType();
}