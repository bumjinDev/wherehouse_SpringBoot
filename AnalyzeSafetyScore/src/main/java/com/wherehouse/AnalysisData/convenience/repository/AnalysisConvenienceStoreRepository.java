package com.wherehouse.AnalysisData.convenience.repository;

import com.wherehouse.AnalysisData.convenience.entity.AnalysisConvenienceStoreData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisConvenienceStoreRepository extends JpaRepository<AnalysisConvenienceStoreData, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 편의점 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CONVENIENCE_STORE_DATA c " +
            "WHERE (c.ROAD_ADDRESS LIKE '서울특별시%' OR c.LOT_ADDRESS LIKE '서울특별시%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findConvenienceStoreCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 편의점 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CONVENIENCE_STORE_DATA c " +
            "WHERE (c.ROAD_ADDRESS LIKE '서울특별시%' OR c.LOT_ADDRESS LIKE '서울특별시%') " +
            "  AND c.DETAILED_STATUS_NAME = '영업중' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveConvenienceStoreCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 편의점 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CONVENIENCE_STORE_DATA c " +
            "WHERE (c.ROAD_ADDRESS LIKE '서울특별시%' OR c.LOT_ADDRESS LIKE '서울특별시%') " +
            "  AND c.DETAILED_STATUS_NAME = '폐업' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(c.ROAD_ADDRESS, c.LOT_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedConvenienceStoreCountByDistrict();
}