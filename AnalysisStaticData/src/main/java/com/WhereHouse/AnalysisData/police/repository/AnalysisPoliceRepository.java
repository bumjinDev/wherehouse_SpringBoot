package com.WhereHouse.AnalysisData.police.repository;

import com.WhereHouse.AnalysisData.police.entity.AnalysisPoliceFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisPoliceRepository extends JpaRepository<AnalysisPoliceFacility, Long> {

    @Query("SELECT a.geocodingStatus, COUNT(a) FROM AnalysisPoliceFacility a GROUP BY a.geocodingStatus")
    List<Object[]> getGeocodingStatistics();

    @Query("SELECT a.districtName, COUNT(a) FROM AnalysisPoliceFacility a " +
            "WHERE a.districtName != '구정보없음' " +
            "GROUP BY a.districtName " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> countFacilitiesByDistrict();

    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE " +
            "a.coordX != 0.0 AND a.coordY != 0.0 AND " +
            "a.coordX IS NOT NULL AND a.coordY IS NOT NULL AND " +
            "a.geocodingStatus = 'SUCCESS'")
    List<AnalysisPoliceFacility> findValidCoordinates();

    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE a.geocodingStatus = 'FAILED'")
    List<AnalysisPoliceFacility> findFailedGeocodingData();

    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE a.geocodingStatus = 'SUCCESS'")
    List<AnalysisPoliceFacility> findSuccessfulGeocodingData();

    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE a.districtName = :districtName")
    List<AnalysisPoliceFacility> findByDistrictName(@Param("districtName") String districtName);
}