package com.wherehouse.AnalysisData.hospital.repository;

import com.wherehouse.AnalysisData.hospital.entity.AnalysisHospitalData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisHospitalRepository extends JpaRepository<AnalysisHospitalData, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 병원 수를 계산하여 반환합니다 (영업/폐업 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    district, " +
            "    COUNT(*) AS count " +
            "FROM ( " +
            "    SELECT " +
            "        CASE " +
            "            WHEN h.ROAD_ADDRESS LIKE '서울특별시%' " +
            "            THEN REGEXP_SUBSTR(h.ROAD_ADDRESS, '서울특별시\\s+([^\\s]+)', 1, 1, '', 1) " +
            "            WHEN h.LOT_ADDRESS LIKE '서울특별시%' " +
            "            THEN REGEXP_SUBSTR(h.LOT_ADDRESS, '서울특별시\\s+([^\\s]+)', 1, 1, '', 1) " +
            "            ELSE NULL " +
            "        END AS district " +
            "    FROM ANALYSIS_HOSPITAL_DATA h " +
            "    WHERE (h.ROAD_ADDRESS LIKE '서울특별시%' OR h.LOT_ADDRESS LIKE '서울특별시%') " +
            ") " +
            "WHERE district IS NOT NULL " +
            "GROUP BY district " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findHospitalCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 영업중인 병원 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(" +
            "        CASE " +
            "            WHEN h.ROAD_ADDRESS LIKE '서울특별시%' THEN h.ROAD_ADDRESS " +
            "            WHEN h.LOT_ADDRESS LIKE '서울특별시%' THEN h.LOT_ADDRESS " +
            "            ELSE NULL " +
            "        END, " +
            "        '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1" +
            "    ) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_HOSPITAL_DATA h " +
            "WHERE (h.ROAD_ADDRESS LIKE '서울특별시%' OR h.LOT_ADDRESS LIKE '서울특별시%') " +
            "  AND h.DETAILED_STATUS_NAME = '영업중' " +
            "  AND REGEXP_SUBSTR(" +
            "      CASE " +
            "          WHEN h.ROAD_ADDRESS LIKE '서울특별시%' THEN h.ROAD_ADDRESS " +
            "          WHEN h.LOT_ADDRESS LIKE '서울특별시%' THEN h.LOT_ADDRESS " +
            "          ELSE NULL " +
            "      END, " +
            "      '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1" +
            "  ) IS NOT NULL " +
            "GROUP BY REGEXP_SUBSTR(" +
            "    CASE " +
            "        WHEN h.ROAD_ADDRESS LIKE '서울특별시%' THEN h.ROAD_ADDRESS " +
            "        WHEN h.LOT_ADDRESS LIKE '서울특별시%' THEN h.LOT_ADDRESS " +
            "        ELSE NULL " +
            "    END, " +
            "    '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1" +
            ") " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveHospitalCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐업한 병원 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(" +
            "        CASE " +
            "            WHEN h.ROAD_ADDRESS LIKE '서울특별시%' THEN h.ROAD_ADDRESS " +
            "            WHEN h.LOT_ADDRESS LIKE '서울특별시%' THEN h.LOT_ADDRESS " +
            "            ELSE NULL " +
            "        END, " +
            "        '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1" +
            "    ) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_HOSPITAL_DATA h " +
            "WHERE (h.ROAD_ADDRESS LIKE '서울특별시%' OR h.LOT_ADDRESS LIKE '서울특별시%') " +
            "  AND h.DETAILED_STATUS_NAME = '폐업' " +
            "  AND REGEXP_SUBSTR(" +
            "      CASE " +
            "          WHEN h.ROAD_ADDRESS LIKE '서울특별시%' THEN h.ROAD_ADDRESS " +
            "          WHEN h.LOT_ADDRESS LIKE '서울특별시%' THEN h.LOT_ADDRESS " +
            "          ELSE NULL " +
            "      END, " +
            "      '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1" +
            "  ) IS NOT NULL " +
            "GROUP BY REGEXP_SUBSTR(" +
            "    CASE " +
            "        WHEN h.ROAD_ADDRESS LIKE '서울특별시%' THEN h.ROAD_ADDRESS " +
            "        WHEN h.LOT_ADDRESS LIKE '서울특별시%' THEN h.LOT_ADDRESS " +
            "        ELSE NULL " +
            "    END, " +
            "    '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1" +
            ") " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedHospitalCountByDistrict();
}