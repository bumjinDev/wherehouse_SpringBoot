package com.WhereHouse.AnalysisData.danran.repository;

import com.WhereHouse.AnalysisData.danran.entity.AnalysisDanranBars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisDanranRepository extends JpaRepository<AnalysisDanranBars, Long> {

    // 구별 단란주점 조회
    List<AnalysisDanranBars> findByDistrictName(String districtName);

    // 영업상태별 조회
    List<AnalysisDanranBars> findByBusinessStatusName(String businessStatusName);

    // 좌표 유효성 검증 (0, 0 좌표 제외)
    @Query("SELECT d FROM AnalysisDanranBars d WHERE d.coordX != 0 AND d.coordY != 0")
    List<AnalysisDanranBars> findValidCoordinates();

    // 지오코딩 실패 데이터 조회
    @Query("SELECT d FROM AnalysisDanranBars d WHERE d.geocodingStatus = 'FAILED' OR d.coordX = 0 OR d.coordY = 0")
    List<AnalysisDanranBars> findFailedGeocodingData();

    // 특정 영역 내 단란주점 조회 (서울시 범위 검증용)
    @Query("SELECT d FROM AnalysisDanranBars d WHERE d.coordY BETWEEN :minLat AND :maxLat AND d.coordX BETWEEN :minLng AND :maxLng")
    List<AnalysisDanranBars> findByLocationBounds(
            @Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng, @Param("maxLng") Double maxLng);

    // 구별 단란주점 밀도 계산용 (업소 수 카운트)
    @Query("SELECT d.districtName, COUNT(d) FROM AnalysisDanranBars d WHERE d.coordX != 0 AND d.coordY != 0 GROUP BY d.districtName ORDER BY COUNT(d) DESC")
    List<Object[]> countDanranBarsByDistrict();

    // 영업 중인 단란주점만 조회
    @Query("SELECT d FROM AnalysisDanranBars d WHERE d.businessStatusName IN ('정상', '영업') AND d.coordX != 0 AND d.coordY != 0")
    List<AnalysisDanranBars> findActiveDanranBarsWithValidCoords();

    // 지오코딩 성공률 통계
    @Query("SELECT d.geocodingStatus, COUNT(d) FROM AnalysisDanranBars d GROUP BY d.geocodingStatus")
    List<Object[]> getGeocodingStatistics();

    // 주소 타입별 지오코딩 성공률 (지번주소 vs 도로명주소)
    @Query("SELECT d.geocodingAddressType, d.geocodingStatus, COUNT(d) FROM AnalysisDanranBars d GROUP BY d.geocodingAddressType, d.geocodingStatus ORDER BY d.geocodingAddressType")
    List<Object[]> getGeocodingStatisticsByAddressType();
}