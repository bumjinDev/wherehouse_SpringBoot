package com.wherehouse.AnalysisData.subway.repository;

import com.wherehouse.AnalysisData.subway.entity.AnalysisSubwayStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisSubwayStationRepository extends JpaRepository<AnalysisSubwayStation, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 지하철역 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_SUBWAY_STATION s " +
            "    WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.JIBUN_ADDRESS LIKE '서울특별시%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSubwayStationCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 좌표 정보가 있는 지하철역 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_SUBWAY_STATION s " +
            "    WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND s.LATITUDE IS NOT NULL " +
            "      AND s.LONGITUDE IS NOT NULL " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSubwayStationWithCoordinatesCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 환승역 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_SUBWAY_STATION s " +
            "    WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND (s.STATION_NAME_KOR LIKE '%,%' OR s.STATION_NAME_KOR LIKE '%호선%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findTransferStationCountByDistrict();

    /**
     * 4번째 메서드: 서울시 자치구별 연락처 정보가 있는 지하철역 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT district, COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_SUBWAY_STATION s " +
            "    WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.JIBUN_ADDRESS LIKE '서울특별시%') " +
            "      AND s.STATION_PHONE IS NOT NULL " +
            "      AND s.STATION_PHONE != '데이터없음' " +
            "      AND REGEXP_LIKE(s.STATION_PHONE, '[0-9]') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSubwayStationWithContactCountByDistrict();

    /**
     * 5번째 메서드: 특정 자치구의 상세 지하철역 현황을 반환합니다
     * @param districtName 자치구명
     * @return List<Object[]>: 각 Object[]는 [역명, 전화번호, 주소, 위도, 경도]로 구성됩니다.
     */
    @Query(value = "SELECT s.STATION_NAME_KOR, s.STATION_PHONE, " +
            "       COALESCE(s.ROAD_ADDRESS, s.JIBUN_ADDRESS) AS address, " +
            "       s.LATITUDE, s.LONGITUDE " +
            "FROM ( " +
            "    SELECT s.*, " +
            "        CASE " +
            "            WHEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.ROAD_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            WHEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) IS NOT NULL " +
            "            THEN REGEXP_SUBSTR(s.JIBUN_ADDRESS, '서울특별시\\s*(강남구|강동구|강북구|강서구|관악구|광진구|구로구|금천구|노원구|도봉구|동대문구|동작구|마포구|서대문구|서초구|성동구|성북구|송파구|양천구|영등포구|용산구|은평구|종로구|중구|중랑구)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_SUBWAY_STATION s " +
            "    WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.JIBUN_ADDRESS LIKE '서울특별시%') " +
            ") s " +
            "WHERE s.district = :districtName " +
            "ORDER BY s.STATION_NAME_KOR",
            nativeQuery = true)
    List<Object[]> findSubwayStationDetailsByDistrict(@Param("districtName") String districtName);
}