package com.WhereHouse.AnalysisData.school.repository;

import com.WhereHouse.AnalysisData.school.entity.AnalysisSchoolStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisSchoolRepository extends JpaRepository<AnalysisSchoolStatistics, Long> {

    List<AnalysisSchoolStatistics> findBySchoolLevel(String schoolLevel);

    List<AnalysisSchoolStatistics> findByOperationStatus(String operationStatus);

    List<AnalysisSchoolStatistics> findByEstablishmentType(String establishmentType);

    boolean existsBySchoolId(String schoolId);

    @Query("SELECT COUNT(a) FROM AnalysisSchoolStatistics a")
    long countAnalysisData();

    @Query("SELECT a.schoolLevel, COUNT(a) FROM AnalysisSchoolStatistics a GROUP BY a.schoolLevel ORDER BY COUNT(a) DESC")
    List<Object[]> findSchoolCountByLevel();

    @Query("SELECT a FROM AnalysisSchoolStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisSchoolStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisSchoolStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.operationStatus, COUNT(a) FROM AnalysisSchoolStatistics a GROUP BY a.operationStatus ORDER BY COUNT(a) DESC")
    List<Object[]> findSchoolCountByOperationStatus();

    @Query("SELECT a.establishmentType, COUNT(a) FROM AnalysisSchoolStatistics a GROUP BY a.establishmentType ORDER BY COUNT(a) DESC")
    List<Object[]> findSchoolCountByEstablishmentType();

    @Query("SELECT a FROM AnalysisSchoolStatistics a WHERE a.schoolName LIKE %:name%")
    List<AnalysisSchoolStatistics> findBySchoolNameContaining(@Param("name") String name);

    @Query("SELECT a FROM AnalysisSchoolStatistics a WHERE a.locationAddress LIKE %:address% OR a.roadAddress LIKE %:address%")
    List<AnalysisSchoolStatistics> findByAddressContaining(@Param("address") String address);
}