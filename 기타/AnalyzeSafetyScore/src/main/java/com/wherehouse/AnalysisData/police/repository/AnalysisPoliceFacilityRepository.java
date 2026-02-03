package com.wherehouse.AnalysisData.police.repository;

import com.wherehouse.AnalysisData.police.entity.AnalysisPoliceFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisPoliceFacilityRepository extends JpaRepository<AnalysisPoliceFacility, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 경찰시설 수를 계산하여 반환합니다
     * DISTRICT_NAME 필드를 사용하여 구별 집계
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    p.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_POLICE_FACILITY p " +
            "WHERE p.DISTRICT_NAME IS NOT NULL " +
            "GROUP BY p.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findPoliceFacilityCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 좌표 정보가 있는 경찰시설 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    p.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_POLICE_FACILITY p " +
            "WHERE p.DISTRICT_NAME IS NOT NULL " +
            "  AND p.COORD_X IS NOT NULL " +
            "  AND p.COORD_Y IS NOT NULL " +
            "  AND p.COORD_Y BETWEEN 33.0 AND 38.7 " +
            "  AND p.COORD_X BETWEEN 124.0 AND 132.0 " +
            "GROUP BY p.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findPoliceFacilityWithCoordinatesByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 시설유형별 경찰시설 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 시설유형(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    p.DISTRICT_NAME, " +
            "    p.FACILITY_TYPE, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_POLICE_FACILITY p " +
            "WHERE p.DISTRICT_NAME IS NOT NULL " +
            "  AND p.FACILITY_TYPE IS NOT NULL " +
            "GROUP BY p.DISTRICT_NAME, p.FACILITY_TYPE " +
            "ORDER BY p.DISTRICT_NAME, count DESC",
            nativeQuery = true)
    List<Object[]> findPoliceFacilityCountByDistrictAndType();
}