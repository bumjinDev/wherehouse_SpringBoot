package com.wherehouse.AnalysisData.cinema.repository;

import com.wherehouse.AnalysisData.cinema.entity.AnalysisCinemaStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisCinemaRepository extends JpaRepository<AnalysisCinemaStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 영화관 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CINEMA_STATISTICS c " +
            "WHERE (c.ROAD_ADDRESS LIKE '서울특별시%' OR c.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findCinemaCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 영화관 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CINEMA_STATISTICS c " +
            "WHERE (c.ROAD_ADDRESS LIKE '서울특별시%' OR c.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "  AND c.BUSINESS_STATUS_NAME like '%정상%' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveCinemaCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 영화관 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CINEMA_STATISTICS c " +
            "WHERE (c.ROAD_ADDRESS LIKE '서울특별시%' OR c.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "  AND c.BUSINESS_STATUS_NAME = '폐업' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedCinemaCountByDistrict();
}