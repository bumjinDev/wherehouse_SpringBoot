package com.wherehouse.AnalysisData.mart.repository;

import com.wherehouse.AnalysisData.mart.entity.AnalysisMartStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisMartRepository extends JpaRepository<AnalysisMartStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 마트 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    CASE " +
            "        WHEN REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "        THEN REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "        ELSE NULL " +
            "    END AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findMartCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 마트 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND m.BUSINESS_STATUS_NAME = '영업' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveMartCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 마트 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND (m.BUSINESS_STATUS_NAME LIKE '%폐업%' OR m.BUSINESS_STATUS_NAME LIKE '%휴업%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedMartCountByDistrict();

    /**
     * 4번째 메서드: 서울시 자치구별 특정 업종 마트 수를 계산하여 반환합니다
     * @param businessType 업종명 (백화점, 대형마트, 슈퍼마켓 등)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND m.BUSINESS_TYPE_NAME LIKE CONCAT('%', :businessType, '%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findMartCountByDistrictAndType(@Param("businessType") String businessType);

    /**
     * 5번째 메서드: 서울시 자치구별 대형 상업시설(백화점+대형마트) 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND (m.BUSINESS_TYPE_NAME LIKE '%백화점%' OR m.BUSINESS_TYPE_NAME LIKE '%대형마트%' OR m.BUSINESS_TYPE_NAME LIKE '%할인점%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findLargeScaleMartCountByDistrict();

    /**
     * 6번째 메서드: 서울시 자치구별 좌표 정보가 있는 마트 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND m.LATITUDE IS NOT NULL " +
            "  AND m.LONGITUDE IS NOT NULL " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findMartWithCoordinatesCountByDistrict();

    /**
     * 7번째 메서드: 서울시 자치구별 영업중인 특정 업종 마트 수를 계산하여 반환합니다
     * @param businessType 업종명
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND m.BUSINESS_STATUS_NAME = '영업' " +
            "  AND m.BUSINESS_TYPE_NAME LIKE CONCAT('%', :businessType, '%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveMartCountByDistrictAndType(@Param("businessType") String businessType);

    /**
     * 8번째 메서드: 특정 자치구의 상세 마트 현황을 반환합니다
     * @param districtName 자치구명
     * @return List<Object[]>: 각 Object[]는 [업체명, 업종명, 영업상태, 전화번호, 주소]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    m.BUSINESS_NAME, " +
            "    m.BUSINESS_TYPE_NAME, " +
            "    m.BUSINESS_STATUS_NAME, " +
            "    m.PHONE_NUMBER, " +
            "    COALESCE(m.ROAD_ADDRESS, m.ADDRESS) AS address " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) = :districtName " +
            "ORDER BY m.BUSINESS_NAME",
            nativeQuery = true)
    List<Object[]> findMartDetailsByDistrict(@Param("districtName") String districtName);

    /**
     * 9번째 메서드: 서울시 자치구별 업종별 마트 수 분포를 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명, 업종명, 개수]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    m.BUSINESS_TYPE_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND m.BUSINESS_TYPE_NAME IS NOT NULL " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1), m.BUSINESS_TYPE_NAME " +
            "HAVING REGEXP_SUBSTR(COALESCE(m.ROAD_ADDRESS, m.ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY district, count DESC",
            nativeQuery = true)
    List<Object[]> findMartTypeDistributionByDistrict();

    /**
     * 10번째 메서드: 서울시 전체 마트 영업상태별 통계를 반환합니다
     * @return List<Object[]>: 각 Object[]는 [영업상태명, 개수]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    m.BUSINESS_STATUS_NAME, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_MART_STATISTICS m " +
            "WHERE (m.ROAD_ADDRESS LIKE '서울특별시%' OR m.ADDRESS LIKE '서울특별시%') " +
            "  AND m.BUSINESS_STATUS_NAME IS NOT NULL " +
            "GROUP BY m.BUSINESS_STATUS_NAME " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findMartStatusStatistics();
}