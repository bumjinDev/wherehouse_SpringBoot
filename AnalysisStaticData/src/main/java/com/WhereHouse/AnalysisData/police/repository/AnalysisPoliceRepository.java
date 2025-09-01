package com.WhereHouse.AnalysisData.police.repository;

import com.WhereHouse.AnalysisData.police.entity.AnalysisPoliceFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisPoliceRepository extends JpaRepository<AnalysisPoliceFacility, Long> {

    // 구별 경찰시설 조회
    List<AnalysisPoliceFacility> findByDistrictName(String districtName);

    // 시설 유형별 조회
    List<AnalysisPoliceFacility> findByFacilityType(String facilityType);

    // 좌표 유효성 검증 (0, 0 좌표 제외)
    @Query("SELECT p FROM AnalysisPoliceFacility p WHERE p.coordX != 0 AND p.coordY != 0")
    List<AnalysisPoliceFacility> findValidCoordinates();

    // 지오코딩 실패 데이터 조회
    @Query("SELECT p FROM AnalysisPoliceFacility p WHERE p.geocodingStatus = 'FAILED' OR p.coordX = 0 OR p.coordY = 0")
    List<AnalysisPoliceFacility> findFailedGeocodingData();

    // 특정 영역 내 경찰시설 조회 (서울시 범위 검증용)
    @Query("SELECT p FROM AnalysisPoliceFacility p WHERE p.coordY BETWEEN :minLat AND :maxLat AND p.coordX BETWEEN :minLng AND :maxLng")
    List<AnalysisPoliceFacility> findByLocationBounds(
            @Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng, @Param("maxLng") Double maxLng);

    // 구별 경찰시설 밀도 계산용 (시설 수 카운트)
    @Query("SELECT p.districtName, COUNT(p) FROM AnalysisPoliceFacility p WHERE p.coordX != 0 AND p.coordY != 0 GROUP BY p.districtName ORDER BY COUNT(p) DESC")
    List<Object[]> countFacilitiesByDistrict();

    // 지구대만 조회
    @Query("SELECT p FROM AnalysisPoliceFacility p WHERE p.facilityType LIKE '%지구대%' AND p.coordX != 0 AND p.coordY != 0")
    List<AnalysisPoliceFacility> findDistrictOfficesWithValidCoords();

    // 파출소만 조회
    @Query("SELECT p FROM AnalysisPoliceFacility p WHERE p.facilityType LIKE '%파출소%' AND p.coordX != 0 AND p.coordY != 0")
    List<AnalysisPoliceFacility> findPoliceBoxesWithValidCoords();

    // 지오코딩 성공률 통계
    @Query("SELECT p.geocodingStatus, COUNT(p) FROM AnalysisPoliceFacility p GROUP BY p.geocodingStatus")
    List<Object[]> getGeocodingStatistics();
}