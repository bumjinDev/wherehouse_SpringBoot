package com.wherehouse.AnalysisData.karaoke.repository;

import com.wherehouse.AnalysisData.karaoke.entity.AnalysisKaraokeRooms;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisKaraokeRoomRepository extends JpaRepository<AnalysisKaraokeRooms, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 노래방 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * DISTRICT_NAME 필드를 사용하여 구별 집계 (이미 전처리된 필드)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    k.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_KARAOKE_ROOMS k " +
            "WHERE k.DISTRICT_NAME IS NOT NULL " +
            "  AND k.DISTRICT_NAME != '' " +
            "GROUP BY k.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findKaraokeRoomCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 노래방 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    k.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_KARAOKE_ROOMS k " +
            "WHERE k.DISTRICT_NAME IS NOT NULL " +
            "  AND k.BUSINESS_STATUS_NAME like '%정상%' " +
            "GROUP BY k.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveKaraokeRoomCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 노래방 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    k.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_KARAOKE_ROOMS k " +
            "WHERE k.DISTRICT_NAME IS NOT NULL " +
            "  AND k.DISTRICT_NAME != '' " +
            "  AND k.BUSINESS_STATUS_NAME = '폐업' " +
            "GROUP BY k.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedKaraokeRoomCountByDistrict();
}