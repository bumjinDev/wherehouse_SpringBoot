package com.wherehouse.AnalysisData.pcbang.repository;

import com.wherehouse.AnalysisData.pcbang.entity.AnalysisPcBangStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisPcBangRepository extends JpaRepository<AnalysisPcBangStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 정상 영업 PC방 수를 계산하여 반환합니다
     * "취소/말소/만료/정지/중지" 또는 "폐업"을 제외한 PC방만 집계
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
//    @Query(value = "SELECT " +
//            "    REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
//            "    COUNT(*) AS count " +
//            "FROM ANALYSIS_PC_BANG_STATISTICS p " +
//            "WHERE (p.ROAD_ADDRESS LIKE '서울특별시%' OR p.JIBUN_ADDRESS LIKE '서울특별시%') " +
//            "  AND p.BUSINESS_STATUS_NAME NOT IN ('취소', '말소', '만료', '정지', '중지', '폐업') " +
//            "  AND p.BUSINESS_STATUS_NAME NOT LIKE '%취소%' " +
//            "  AND p.BUSINESS_STATUS_NAME NOT LIKE '%말소%' " +
//            "  AND p.BUSINESS_STATUS_NAME NOT LIKE '%만료%' " +
//            "  AND p.BUSINESS_STATUS_NAME NOT LIKE '%정지%' " +
//            "  AND p.BUSINESS_STATUS_NAME NOT LIKE '%중지%' " +
//            "  AND p.BUSINESS_STATUS_NAME NOT LIKE '%폐업%' " +
//            "GROUP BY REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
//            "HAVING REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
//            "ORDER BY count DESC",
//            nativeQuery = true)
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_PC_BANG_STATISTICS p " +
            "WHERE (p.ROAD_ADDRESS LIKE '서울특별시%' OR p.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "  AND p.BUSINESS_STATUS_NAME LIKE '%정상%' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActivePcBangCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 전체 PC방 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_PC_BANG_STATISTICS p " +
            "WHERE (p.ROAD_ADDRESS LIKE '서울특별시%' OR p.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findAllPcBangCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업/정지 PC방 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_PC_BANG_STATISTICS p " +
            "WHERE (p.ROAD_ADDRESS LIKE '서울특별시%' OR p.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "  AND (p.BUSINESS_STATUS_NAME IN ('취소', '말소', '만료', '정지', '중지', '폐업') " +
            "       OR p.BUSINESS_STATUS_NAME LIKE '%취소%' " +
            "       OR p.BUSINESS_STATUS_NAME LIKE '%말소%' " +
            "       OR p.BUSINESS_STATUS_NAME LIKE '%만료%' " +
            "       OR p.BUSINESS_STATUS_NAME LIKE '%정지%' " +
            "       OR p.BUSINESS_STATUS_NAME LIKE '%중지%' " +
            "       OR p.BUSINESS_STATUS_NAME LIKE '%폐업%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(p.ROAD_ADDRESS, p.JIBUN_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findInactivePcBangCountByDistrict();
}