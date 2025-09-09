package com.wherehouse.AnalysisData.streetlight.repository;

import com.wherehouse.AnalysisData.streetlight.entity.AnalysisStreetlightStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisStreetlightRepository extends JpaRepository<AnalysisStreetlightStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 가로등 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    s.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_STREETLIGHT_STATISTICS s " +
            "WHERE s.DISTRICT_NAME IS NOT NULL " +
            "GROUP BY s.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findStreetlightCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 좌표 정보가 있는 가로등 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    s.DISTRICT_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_STREETLIGHT_STATISTICS s " +
            "WHERE s.DISTRICT_NAME IS NOT NULL " +
            "  AND s.LATITUDE IS NOT NULL " +
            "  AND s.LONGITUDE IS NOT NULL " +
            "  AND s.LATITUDE BETWEEN 33.0 AND 38.7 " +
            "  AND s.LONGITUDE BETWEEN 124.0 AND 132.0 " +
            "GROUP BY s.DISTRICT_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findStreetlightWithCoordinatesByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 동별 가로등 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 동명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    s.DISTRICT_NAME, " +
            "    s.DONG_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_STREETLIGHT_STATISTICS s " +
            "WHERE s.DISTRICT_NAME IS NOT NULL " +
            "  AND s.DONG_NAME IS NOT NULL " +
            "GROUP BY s.DISTRICT_NAME, s.DONG_NAME " +
            "ORDER BY s.DISTRICT_NAME, count DESC",
            nativeQuery = true)
    List<Object[]> findStreetlightCountByDistrictAndDong();
}