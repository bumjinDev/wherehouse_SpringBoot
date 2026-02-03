package com.wherehouse.AnalysisData.lodging.repository;

import com.wherehouse.AnalysisData.lodging.entity.AnalysisLodgingStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisLodgingRepository extends JpaRepository<AnalysisLodgingStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 숙박업 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findLodgingCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 숙박업 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            "      AND l.BUSINESS_STATUS_NAME = '영업' " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveLodgingCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 숙박업 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            "      AND (l.BUSINESS_STATUS_NAME LIKE '%폐업%' OR l.BUSINESS_STATUS_NAME LIKE '%휴업%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedLodgingCountByDistrict();

    /**
     * 4번째 메서드: 서울시 자치구별 특정 숙박업 유형 수를 계산하여 반환합니다
     * @param lodgingType 숙박업 유형 (호텔, 여관, 모텔 등)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            "      AND l.BUSINESS_TYPE_NAME LIKE CONCAT('%', :lodgingType, '%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findLodgingCountByDistrictAndType(@Param("lodgingType") String lodgingType);

    /**
     * 5번째 메서드: 서울시 자치구별 고급 숙박시설(호텔, 리조트 등) 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            "      AND (l.BUSINESS_TYPE_NAME LIKE '%호텔%' OR l.BUSINESS_TYPE_NAME LIKE '%리조트%' OR l.BUSINESS_TYPE_NAME LIKE '%특급%' OR l.BUSINESS_TYPE_NAME LIKE '%5성%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findHighEndLodgingCountByDistrict();

    /**
     * 6번째 메서드: 서울시 자치구별 관광 숙박시설 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            "      AND (l.BUSINESS_TYPE_NAME LIKE '%호텔%' OR l.BUSINESS_TYPE_NAME LIKE '%펜션%' OR l.BUSINESS_TYPE_NAME LIKE '%리조트%' OR l.HYGIENE_BUSINESS_TYPE LIKE '%관광%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findTourismLodgingCountByDistrict();

    /**
     * 7번째 메서드: 서울시 자치구별 좌표 정보가 있는 숙박업 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            "      AND l.LATITUDE IS NOT NULL " +
            "      AND l.LONGITUDE IS NOT NULL " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findLodgingWithCoordinatesCountByDistrict();

    /**
     * 8번째 메서드: 특정 자치구의 상세 숙박업 현황을 반환합니다
     * @param districtName 자치구명
     * @return List<Object[]>: 각 Object[]는 [업체명, 업종명, 영업상태, 주소]로 구성됩니다.
     */
    @Query(value = "SELECT l.BUSINESS_NAME, l.BUSINESS_TYPE_NAME, l.BUSINESS_STATUS_NAME, " +
            "       COALESCE(l.ROAD_ADDRESS, l.FULL_ADDRESS) AS address " +
            "FROM ( " +
            "    SELECT l.*, " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(l.FULL_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_LODGING_STATISTICS l " +
            "    WHERE (l.ROAD_ADDRESS LIKE '서울특별시%' OR l.FULL_ADDRESS LIKE '서울특별시%') " +
            ") l " +
            "WHERE l.district = :districtName " +
            "ORDER BY l.BUSINESS_NAME",
            nativeQuery = true)
    List<Object[]> findLodgingDetailsByDistrict(@Param("districtName") String districtName);
}