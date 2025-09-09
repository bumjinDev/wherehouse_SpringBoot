package com.WhereHouse.AnalysisData.police.repository;

import com.WhereHouse.AnalysisData.police.entity.AnalysisPoliceFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 분석용 경찰시설 데이터 Repository
 *
 * @author Safety Analysis System
 * @since 1.0
 */
@Repository
public interface AnalysisPoliceRepository extends JpaRepository<AnalysisPoliceFacility, Long> {

    /**
     * 지오코딩 처리 상태별 통계 조회
     *
     * @return [상태, 개수] 형태의 Object 배열 리스트
     */
    @Query("SELECT a.geocodingStatus, COUNT(a) FROM AnalysisPoliceFacility a GROUP BY a.geocodingStatus")
    List<Object[]> getGeocodingStatistics();

    /**
     * 구별 경찰시설 개수 조회 (밀도 순위용)
     *
     * @return [구이름, 시설개수] 형태의 Object 배열 리스트 (시설 개수 내림차순)
     */
    @Query("SELECT a.districtName, COUNT(a) FROM AnalysisPoliceFacility a " +
            "WHERE a.districtName != '구정보없음' " +
            "GROUP BY a.districtName " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> countFacilitiesByDistrict();

    /**
     * 유효한 좌표를 가진 경찰시설 조회
     *
     * @return 유효한 좌표를 가진 경찰시설 리스트
     */
    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE " +
            "a.coordX != 0.0 AND a.coordY != 0.0 AND " +
            "a.coordX IS NOT NULL AND a.coordY IS NOT NULL AND " +
            "a.geocodingStatus = 'SUCCESS'")
    List<AnalysisPoliceFacility> findValidCoordinates();

    /**
     * 지오코딩 실패 데이터 조회
     *
     * @return 지오코딩 실패한 경찰시설 리스트
     */
    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE a.geocodingStatus = 'FAILED'")
    List<AnalysisPoliceFacility> findFailedGeocodingData();

    /**
     * 서울시 구별 경찰시설 개수 조회 (구 이름 있는 것만)
     *
     * @return [구이름, 시설개수] 형태의 Object 배열 리스트
     */
    @Query("SELECT a.districtName, COUNT(a) FROM AnalysisPoliceFacility a " +
            "WHERE a.districtName IS NOT NULL AND a.districtName != '구정보없음' " +
            "GROUP BY a.districtName " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> getDistrictFacilityCount();

    /**
     * 지오코딩 성공한 데이터만 조회
     *
     * @return 지오코딩 성공한 경찰시설 리스트
     */
    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE a.geocodingStatus = 'SUCCESS'")
    List<AnalysisPoliceFacility> findSuccessfulGeocodingData();

    /**
     * 특정 구의 경찰시설 조회
     *
     * @param districtName 구 이름 (예: "강남구")
     * @return 해당 구의 경찰시설 리스트
     */
    @Query("SELECT a FROM AnalysisPoliceFacility a WHERE a.districtName = :districtName")
    List<AnalysisPoliceFacility> findByDistrictName(String districtName);
}