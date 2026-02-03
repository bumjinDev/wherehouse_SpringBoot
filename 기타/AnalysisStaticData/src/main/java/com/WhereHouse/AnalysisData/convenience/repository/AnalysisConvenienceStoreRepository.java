package com.WhereHouse.AnalysisData.convenience.repository;

import com.WhereHouse.AnalysisData.convenience.entity.AnalysisConvenienceStoreData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 편의점 분석용 데이터 Repository
 * 통계 쿼리 및 데이터 검증을 위한 메서드 제공
 */
@Repository
public interface AnalysisConvenienceStoreRepository extends JpaRepository<AnalysisConvenienceStoreData, Long> {

    /**
     * 전체 데이터 개수 조회
     */
    @Query("SELECT COUNT(a) FROM AnalysisConvenienceStoreData a")
    long getTotalCount();

    /**
     * 좌표 정보 보유 데이터 개수 조회
     */
    @Query("SELECT COUNT(a) FROM AnalysisConvenienceStoreData a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    long getCoordinateCount();

    /**
     * 좌표 완성도 비율 계산
     */
    @Query("SELECT CAST(COUNT(CASE WHEN a.latitude IS NOT NULL AND a.longitude IS NOT NULL THEN 1 END) AS double) / CAST(COUNT(a) AS double) * 100 " +
            "FROM AnalysisConvenienceStoreData a")
    Double getCoordinateCompletionRate();

    /**
     * 상세영업상태별 분포 조회
     */
    @Query("SELECT a.detailedStatusName, COUNT(a) " +
            "FROM AnalysisConvenienceStoreData a " +
            "GROUP BY a.detailedStatusName " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> getStatusDistribution();

    /**
     * 좌표 정보가 있는 데이터만 조회
     */
    @Query("SELECT a FROM AnalysisConvenienceStoreData a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisConvenienceStoreData> findAllWithCoordinates();

    /**
     * 특정 상세영업상태의 데이터 개수 조회
     */
    @Query("SELECT COUNT(a) FROM AnalysisConvenienceStoreData a WHERE a.detailedStatusName = :statusName")
    long countByDetailedStatusName(String statusName);

    /**
     * 영업중인 편의점만 조회
     */
    @Query("SELECT a FROM AnalysisConvenienceStoreData a WHERE a.detailedStatusName LIKE '%영업%'")
    List<AnalysisConvenienceStoreData> findActiveStores();

    /**
     * 특정 지역(주소 포함) 편의점 조회
     */
    @Query("SELECT a FROM AnalysisConvenienceStoreData a " +
            "WHERE a.roadAddress LIKE %:region% OR a.lotAddress LIKE %:region%")
    List<AnalysisConvenienceStoreData> findByRegion(String region);

    /**
     * 좌표 범위로 편의점 조회 (특정 지역 내)
     */
    @Query("SELECT a FROM AnalysisConvenienceStoreData a " +
            "WHERE a.latitude BETWEEN :minLat AND :maxLat " +
            "AND a.longitude BETWEEN :minLng AND :maxLng")
    List<AnalysisConvenienceStoreData> findByCoordinateRange(
            Double minLat, Double maxLat, Double minLng, Double maxLng);

    /**
     * 데이터 품질 검증을 위한 통계 정보 조회
     */
    @Query("SELECT " +
            "COUNT(a) as totalCount, " +
            "COUNT(CASE WHEN a.latitude IS NOT NULL AND a.longitude IS NOT NULL THEN 1 END) as coordinateCount, " +
            "COUNT(CASE WHEN a.businessName != '데이터없음' THEN 1 END) as validBusinessNameCount, " +
            "COUNT(CASE WHEN a.phoneNumber != '데이터없음' THEN 1 END) as validPhoneCount " +
            "FROM AnalysisConvenienceStoreData a")
    Object[] getDataQualityStats();

    /**
     * 주소별 편의점 밀도 조회 (시/구 단위)
     */
    @Query("SELECT " +
            "CASE " +
            "  WHEN a.roadAddress IS NOT NULL AND a.roadAddress != '데이터없음' " +
            "  THEN SUBSTRING(a.roadAddress, 1, LOCATE(' ', a.roadAddress, LOCATE(' ', a.roadAddress) + 1)) " +
            "  ELSE SUBSTRING(a.lotAddress, 1, LOCATE(' ', a.lotAddress, LOCATE(' ', a.lotAddress) + 1)) " +
            "END as region, " +
            "COUNT(a) as storeCount " +
            "FROM AnalysisConvenienceStoreData a " +
            "WHERE (a.roadAddress IS NOT NULL AND a.roadAddress != '데이터없음') " +
            "   OR (a.lotAddress IS NOT NULL AND a.lotAddress != '데이터없음') " +
            "GROUP BY " +
            "CASE " +
            "  WHEN a.roadAddress IS NOT NULL AND a.roadAddress != '데이터없음' " +
            "  THEN SUBSTRING(a.roadAddress, 1, LOCATE(' ', a.roadAddress, LOCATE(' ', a.roadAddress) + 1)) " +
            "  ELSE SUBSTRING(a.lotAddress, 1, LOCATE(' ', a.lotAddress, LOCATE(' ', a.lotAddress) + 1)) " +
            "END " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> getRegionalDensity();

    /**
     * 최근 저장된 데이터 조회 (상위 N개)
     */
    @Query("SELECT a FROM AnalysisConvenienceStoreData a ORDER BY a.id DESC")
    List<AnalysisConvenienceStoreData> findRecentData(org.springframework.data.domain.Pageable pageable);

    /**
     * 중복 사업장명 조회
     */
    @Query("SELECT a.businessName, COUNT(a) " +
            "FROM AnalysisConvenienceStoreData a " +
            "WHERE a.businessName != '데이터없음' " +
            "GROUP BY a.businessName " +
            "HAVING COUNT(a) > 1 " +
            "ORDER BY COUNT(a) DESC")
    List<Object[]> findDuplicateBusinessNames();
}