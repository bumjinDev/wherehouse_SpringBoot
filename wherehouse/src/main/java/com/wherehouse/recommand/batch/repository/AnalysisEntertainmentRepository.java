package com.wherehouse.recommand.batch.repository;

import com.wherehouse.recommand.batch.entity.AnalysisEntertainmentStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisEntertainmentRepository extends JpaRepository<AnalysisEntertainmentStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 유흥업소 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findEntertainmentCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 유흥업소 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND e.BUSINESS_STATUS_NAME = '영업중' " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveEntertainmentCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 유흥업소 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND (e.BUSINESS_STATUS_NAME LIKE '%폐업%' OR e.BUSINESS_STATUS_NAME LIKE '%휴업%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedEntertainmentCountByDistrict();

    /**
     * 4번째 메서드: 서울시 자치구별 특정 카테고리 유흥업소 수를 계산하여 반환합니다
     * @param category 유흥업소 카테고리 (유흥주점, 단란주점, 노래연습장 등)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND e.BUSINESS_CATEGORY LIKE CONCAT('%', :category, '%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findEntertainmentCountByDistrictAndCategory(@Param("category") String category);

    /**
     * 5번째 메서드: 서울시 자치구별 고급 유흥업소 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND (e.BUSINESS_CATEGORY LIKE '%클럽%' OR e.BUSINESS_CATEGORY LIKE '%대형%' OR e.BUSINESS_NAME LIKE '%클럽%' " +
            "           OR (e.BUSINESS_CATEGORY LIKE '%유흥주점%' AND e.BUSINESS_NAME LIKE '%호텔%')) " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findHighEndEntertainmentCountByDistrict();

    /**
     * 6번째 메서드: 서울시 자치구별 소규모 유흥업소 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND (e.BUSINESS_CATEGORY LIKE '%노래연습장%' OR e.BUSINESS_CATEGORY LIKE '%노래방%' " +
            "           OR e.BUSINESS_CATEGORY LIKE '%당구장%' OR e.BUSINESS_CATEGORY LIKE '%포켓볼%' " +
            "           OR e.BUSINESS_CATEGORY LIKE '%게임%' OR e.BUSINESS_CATEGORY LIKE '%오락%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSmallScaleEntertainmentCountByDistrict();

    /**
     * 7번째 메서드: 서울시 자치구별 좌표 정보가 있는 유흥업소 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND e.LATITUDE IS NOT NULL " +
            "      AND e.LONGITUDE IS NOT NULL " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findEntertainmentWithCoordinatesCountByDistrict();

    /**
     * 8번째 메서드: 특정 자치구의 상세 유흥업소 현황을 반환합니다
     * @param districtName 자치구명
     * @return List<Object[]>: 각 Object[]는 [업체명, 카테고리, 영업상태, 주소]로 구성됩니다.
     */
    @Query(value = "SELECT e.BUSINESS_NAME, e.BUSINESS_CATEGORY, e.BUSINESS_STATUS_NAME, " +
            "       COALESCE(e.ROAD_ADDRESS, e.JIBUN_ADDRESS) AS address " +
            "FROM ( " +
            "    SELECT e.*, " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(e.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_ENTERTAINMENT_STATISTICS e " +
            "    WHERE (e.ROAD_ADDRESS LIKE '서울특별시%' OR e.JIBUN_ADDRESS LIKE '서울특별시%') " +
            ") e " +
            "WHERE e.district = :districtName " +
            "ORDER BY e.BUSINESS_NAME",
            nativeQuery = true)
    List<Object[]> findEntertainmentDetailsByDistrict(@Param("districtName") String districtName);
}