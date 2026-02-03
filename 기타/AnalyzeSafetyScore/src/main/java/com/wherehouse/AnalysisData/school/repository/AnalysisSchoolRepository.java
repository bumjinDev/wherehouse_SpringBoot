package com.wherehouse.AnalysisData.school.repository;

import com.wherehouse.AnalysisData.school.entity.AnalysisSchoolStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisSchoolRepository extends JpaRepository<AnalysisSchoolStatistics, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 학교 수를 계산하여 반환합니다 (운영/폐교 상관없이 전체)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSchoolCountByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 운영중인 학교 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.OPERATION_STATUS = '운영' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findActiveSchoolCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 폐교한 학교 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.OPERATION_STATUS != '운영' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findClosedSchoolCountByDistrict();

    /**
     * 4번째 메서드: 서울시 자치구별 특정 학교급 학교 수를 계산하여 반환합니다
     * @param schoolLevel 학교급 (초등학교, 중학교, 고등학교 등)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.SCHOOL_LEVEL LIKE CONCAT('%', :schoolLevel, '%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSchoolCountByDistrictAndLevel(@Param("schoolLevel") String schoolLevel);

    /**
     * 5번째 메서드: 서울시 자치구별 특정 설립유형 학교 수를 계산하여 반환합니다
     * @param establishmentType 설립유형 (공립, 사립 등)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.ESTABLISHMENT_TYPE LIKE CONCAT('%', :establishmentType, '%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSchoolCountByDistrictAndEstablishment(@Param("establishmentType") String establishmentType);

    /**
     * 6번째 메서드: 서울시 자치구별 좌표 정보가 있는 학교 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.LATITUDE IS NOT NULL " +
            "  AND s.LONGITUDE IS NOT NULL " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSchoolWithCoordinatesCountByDistrict();

    /**
     * 7번째 메서드: 서울시 자치구별 본교 수를 계산하여 반환합니다 (분교 제외)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.MAIN_BRANCH_TYPE = '본교' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findMainSchoolCountByDistrict();

    /**
     * 8번째 메서드: 서울시 자치구별 분교 수를 계산하여 반환합니다
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.MAIN_BRANCH_TYPE = '분교' " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findBranchSchoolCountByDistrict();

    /**
     * 9번째 메서드: 서울시 자치구별 특정 교육청 소속 학교 수를 계산하여 반환합니다
     * @param educationOfficeName 교육청명
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 개수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) AS district, " +
            "    COUNT(*) AS count " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND s.EDUCATION_OFFICE_NAME LIKE CONCAT('%', :educationOfficeName, '%') " +
            "GROUP BY REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) " +
            "HAVING REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) IS NOT NULL " +
            "ORDER BY count DESC",
            nativeQuery = true)
    List<Object[]> findSchoolCountByDistrictAndEducationOffice(@Param("educationOfficeName") String educationOfficeName);

    /**
     * 10번째 메서드: 특정 자치구의 상세 학교 현황을 반환합니다
     * @param districtName 자치구명
     * @return List<Object[]>: 각 Object[]는 [학교명, 학교급, 설립유형, 운영상태, 본분교구분]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    s.SCHOOL_NAME, " +
            "    s.SCHOOL_LEVEL, " +
            "    s.ESTABLISHMENT_TYPE, " +
            "    s.OPERATION_STATUS, " +
            "    s.MAIN_BRANCH_TYPE " +
            "FROM ANALYSIS_SCHOOL_STATISTICS s " +
            "WHERE (s.ROAD_ADDRESS LIKE '서울특별시%' OR s.LOCATION_ADDRESS LIKE '서울특별시%') " +
            "  AND REGEXP_SUBSTR(COALESCE(s.ROAD_ADDRESS, s.LOCATION_ADDRESS), '서울특별시\\s+([가-힣]+구)', 1, 1, '', 1) = :districtName " +
            "ORDER BY s.SCHOOL_NAME",
            nativeQuery = true)
    List<Object[]> findSchoolDetailsByDistrict(@Param("districtName") String districtName);
}