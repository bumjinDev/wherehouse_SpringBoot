package com.wherehouse.AnalysisData.residentcenter.repository;

import com.wherehouse.AnalysisData.residentcenter.entity.AnalysisResidentCenterStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisResidentCenterRepository extends JpaRepository<AnalysisResidentCenterStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 주민센터 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_RESIDENT_CENTER_STATISTICS r " +
            "    WHERE (r.SIDO LIKE '%서울%' OR r.ADDRESS LIKE '서울특별시%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findResidentCenterCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 좌표 정보가 있는 주민센터 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_RESIDENT_CENTER_STATISTICS r " +
            "    WHERE (r.SIDO LIKE '%서울%' OR r.ADDRESS LIKE '서울특별시%') " +
            "      AND r.LATITUDE IS NOT NULL " +
            "      AND r.LONGITUDE IS NOT NULL " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findResidentCenterWithCoordinatesCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 특정 읍면동 유형 주민센터 수를 계산하여 반환합니다
     * @param eupmeondongType 읍면동 유형 (읍/면/동)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_RESIDENT_CENTER_STATISTICS r " +
            "    WHERE (r.SIDO LIKE '%서울%' OR r.ADDRESS LIKE '서울특별시%') " +
            "      AND r.EUPMEONDONG LIKE CONCAT('%', :eupmeondongType) " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findResidentCenterCountByDistrictAndType(@Param("eupmeondongType") String eupmeondongType);

    /**
     * 4번째 메서드: 서울시 자치구별 완전한 행정구역 정보가 있는 주민센터 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_RESIDENT_CENTER_STATISTICS r " +
            "    WHERE (r.SIDO LIKE '%서울%' OR r.ADDRESS LIKE '서울특별시%') " +
            "      AND r.SIDO IS NOT NULL AND r.SIDO != '데이터없음' " +
            "      AND r.SIGUNGU IS NOT NULL AND r.SIGUNGU != '데이터없음' " +
            "      AND r.EUPMEONDONG IS NOT NULL AND r.EUPMEONDONG != '데이터없음' " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findCompleteAdminInfoResidentCenterCountByDistrict();

    /**
     * 5번째 메서드: 특정 자치구의 상세 주민센터 현황을 반환합니다
     * @param districtName 자치구명
     * @return List<Object[]>: 각 Object[]는 [읍면동명, 주소, 위도, 경도]로 구성됩니다.
     */
    @Query(value = "SELECT r.EUPMEONDONG, r.ADDRESS, r.LATITUDE, r.LONGITUDE " +
            "FROM ( " +
            "    SELECT r.*, " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(REPLACE(r.SIGUNGU, ' ', ''), '(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(r.ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_RESIDENT_CENTER_STATISTICS r " +
            "    WHERE (r.SIDO LIKE '%서울%' OR r.ADDRESS LIKE '서울특별시%') " +
            ") r " +
            "WHERE r.district = :districtName " +
            "ORDER BY r.EUPMEONDONG",
            nativeQuery = true)
    List<Object[]> findResidentCenterDetailsByDistrict(@Param("districtName") String districtName);
}