package com.WhereHouse.AnalysisData.karaoke.repository;

import com.WhereHouse.AnalysisData.karaoke.entity.AnalysisKaraokeRooms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisKaraokeRepository extends JpaRepository<AnalysisKaraokeRooms, Long> {

    // 구별 노래연습장 조회
    List<AnalysisKaraokeRooms> findByDistrictName(String districtName);

    // 영업상태별 조회
    List<AnalysisKaraokeRooms> findByBusinessStatusName(String businessStatusName);

    // 좌표 유효성 검증 (0, 0 좌표 제외)
    @Query("SELECT k FROM AnalysisKaraokeRooms k WHERE k.coordX != 0 AND k.coordY != 0")
    List<AnalysisKaraokeRooms> findValidCoordinates();

    // 지오코딩 실패 데이터 조회
    @Query("SELECT k FROM AnalysisKaraokeRooms k WHERE k.geocodingStatus = 'FAILED' OR k.coordX = 0 OR k.coordY = 0")
    List<AnalysisKaraokeRooms> findFailedGeocodingData();

    // 특정 영역 내 노래연습장 조회 (서울시 범위 검증용)
    @Query("SELECT k FROM AnalysisKaraokeRooms k WHERE k.coordY BETWEEN :minLat AND :maxLat AND k.coordX BETWEEN :minLng AND :maxLng")
    List<AnalysisKaraokeRooms> findByLocationBounds(
            @Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng, @Param("maxLng") Double maxLng);

    // 구별 노래연습장 밀도 계산용 (업소 수 카운트)
    @Query("SELECT k.districtName, COUNT(k) FROM AnalysisKaraokeRooms k WHERE k.coordX != 0 AND k.coordY != 0 GROUP BY k.districtName ORDER BY COUNT(k) DESC")
    List<Object[]> countKaraokeRoomsByDistrict();

    // 영업 중인 노래연습장만 조회
    @Query("SELECT k FROM AnalysisKaraokeRooms k WHERE k.businessStatusName IN ('정상', '영업') AND k.coordX != 0 AND k.coordY != 0")
    List<AnalysisKaraokeRooms> findActiveKaraokeRoomsWithValidCoords();

    // 지오코딩 성공률 통계
    @Query("SELECT k.geocodingStatus, COUNT(k) FROM AnalysisKaraokeRooms k GROUP BY k.geocodingStatus")
    List<Object[]> getGeocodingStatistics();

    // 주소 타입별 지오코딩 성공률 (지번주소 vs 도로명주소)
    @Query("SELECT k.geocodingAddressType, k.geocodingStatus, COUNT(k) FROM AnalysisKaraokeRooms k GROUP BY k.geocodingAddressType, k.geocodingStatus ORDER BY k.geocodingAddressType")
    List<Object[]> getGeocodingStatisticsByAddressType();
}