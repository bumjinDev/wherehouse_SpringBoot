package com.wherehouse.AnalysisData.cctv.repository;

import com.wherehouse.AnalysisData.cctv.entity.AnalysisCctvStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisCctvRepository extends JpaRepository<AnalysisCctvStatistics, Long> {

    /**
     * 서울시 자치구별 CCTV 수를 계산하여 반환합니다. (Native Query 사용)
     * MANAGEMENT_AGENCY 필드의 다양한 형태를 모두 고려하여 구 정보를 추출합니다.
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    CASE " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '서울특별시%구청' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '서울시%구청' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '서울시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '%구청' AND a.MANAGEMENT_AGENCY NOT LIKE '%시%' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '([가-힣]+구)청', 1, 1, '', 1) " +
            "        ELSE NULL " +
            "    END AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_CCTV_STATISTICS a " +
            "WHERE a.MANAGEMENT_AGENCY IS NOT NULL " +
            "  AND (a.MANAGEMENT_AGENCY LIKE '서울특별시%구청' " +
            "       OR a.MANAGEMENT_AGENCY LIKE '서울시%구청' " +
            "       OR (a.MANAGEMENT_AGENCY LIKE '%구청' " +
            "           AND a.MANAGEMENT_AGENCY IN ('강남구청', '강동구청', '강북구청', '강서구청', " +
            "                                       '관악구청', '광진구청', '구로구청', '금천구청', " +
            "                                       '노원구청', '도봉구청', '동대문구청', '동작구청', " +
            "                                       '마포구청', '서대문구청', '서초구청', '성동구청', " +
            "                                       '성북구청', '송파구청', '양천구청', '영등포구청', " +
            "                                       '용산구청', '은평구청', '종로구청', '중구청', '중랑구청'))) " +
            "GROUP BY CASE " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '서울특별시%구청' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '서울시%구청' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '서울시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '%구청' AND a.MANAGEMENT_AGENCY NOT LIKE '%시%' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '([가-힣]+구)청', 1, 1, '', 1) " +
            "        ELSE NULL " +
            "    END " +
            "HAVING CASE " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '서울특별시%구청' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '서울시%구청' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '서울시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "        WHEN a.MANAGEMENT_AGENCY LIKE '%구청' AND a.MANAGEMENT_AGENCY NOT LIKE '%시%' " +
            "        THEN REGEXP_SUBSTR(a.MANAGEMENT_AGENCY, '([가-힣]+구)청', 1, 1, '', 1) " +
            "        ELSE NULL " +
            "    END IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findCctvCountByDistrict();
}