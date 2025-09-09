package com.wherehouse.AnalysisData.university.repository;

import com.wherehouse.AnalysisData.university.dto.DistrictUniversityCountDto;
import com.wherehouse.AnalysisData.university.entity.AnalysisUniversityStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisUniversityRepository extends JpaRepository<AnalysisUniversityStatistics, Long> {

    /**
     * 서울시 자치구별 대학교 통계를 집계하여 DTO 리스트로 반환합니다.
     * 주소에서 구 정보를 추출하여 집계합니다.
     * @return DistrictUniversityCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.university.dto.DistrictUniversityCountDto(" +
            "CASE " +
            "    WHEN a.roadAddress LIKE '%강남구%' THEN '강남구' " +
            "    WHEN a.roadAddress LIKE '%강동구%' THEN '강동구' " +
            "    WHEN a.roadAddress LIKE '%강북구%' THEN '강북구' " +
            "    WHEN a.roadAddress LIKE '%강서구%' THEN '강서구' " +
            "    WHEN a.roadAddress LIKE '%관악구%' THEN '관악구' " +
            "    WHEN a.roadAddress LIKE '%광진구%' THEN '광진구' " +
            "    WHEN a.roadAddress LIKE '%구로구%' THEN '구로구' " +
            "    WHEN a.roadAddress LIKE '%금천구%' THEN '금천구' " +
            "    WHEN a.roadAddress LIKE '%노원구%' THEN '노원구' " +
            "    WHEN a.roadAddress LIKE '%도봉구%' THEN '도봉구' " +
            "    WHEN a.roadAddress LIKE '%동대문구%' THEN '동대문구' " +
            "    WHEN a.roadAddress LIKE '%동작구%' THEN '동작구' " +
            "    WHEN a.roadAddress LIKE '%마포구%' THEN '마포구' " +
            "    WHEN a.roadAddress LIKE '%서대문구%' THEN '서대문구' " +
            "    WHEN a.roadAddress LIKE '%서초구%' THEN '서초구' " +
            "    WHEN a.roadAddress LIKE '%성동구%' THEN '성동구' " +
            "    WHEN a.roadAddress LIKE '%성북구%' THEN '성북구' " +
            "    WHEN a.roadAddress LIKE '%송파구%' THEN '송파구' " +
            "    WHEN a.roadAddress LIKE '%양천구%' THEN '양천구' " +
            "    WHEN a.roadAddress LIKE '%영등포구%' THEN '영등포구' " +
            "    WHEN a.roadAddress LIKE '%용산구%' THEN '용산구' " +
            "    WHEN a.roadAddress LIKE '%은평구%' THEN '은평구' " +
            "    WHEN a.roadAddress LIKE '%종로구%' THEN '종로구' " +
            "    WHEN a.roadAddress LIKE '%중구%' THEN '중구' " +
            "    WHEN a.roadAddress LIKE '%중랑구%' THEN '중랑구' " +
            "    ELSE '기타' " +
            "END, " +
            "COUNT(a.id), " +
            "SUM(CASE WHEN a.establishmentType = '국립' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.establishmentType = '공립' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.establishmentType = '사립' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.schoolType = '전문대학' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.schoolType = '대학교' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.schoolType LIKE '%대학원%' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN a.schoolType NOT IN ('전문대학', '대학교') AND a.schoolType NOT LIKE '%대학원%' THEN 1 ELSE 0 END)" +
            ") " +
            "FROM AnalysisUniversityStatistics a " +
            "WHERE a.sidoName = '서울특별시' " +
            "GROUP BY CASE " +
            "    WHEN a.roadAddress LIKE '%강남구%' THEN '강남구' " +
            "    WHEN a.roadAddress LIKE '%강동구%' THEN '강동구' " +
            "    WHEN a.roadAddress LIKE '%강북구%' THEN '강북구' " +
            "    WHEN a.roadAddress LIKE '%강서구%' THEN '강서구' " +
            "    WHEN a.roadAddress LIKE '%관악구%' THEN '관악구' " +
            "    WHEN a.roadAddress LIKE '%광진구%' THEN '광진구' " +
            "    WHEN a.roadAddress LIKE '%구로구%' THEN '구로구' " +
            "    WHEN a.roadAddress LIKE '%금천구%' THEN '금천구' " +
            "    WHEN a.roadAddress LIKE '%노원구%' THEN '노원구' " +
            "    WHEN a.roadAddress LIKE '%도봉구%' THEN '도봉구' " +
            "    WHEN a.roadAddress LIKE '%동대문구%' THEN '동대문구' " +
            "    WHEN a.roadAddress LIKE '%동작구%' THEN '동작구' " +
            "    WHEN a.roadAddress LIKE '%마포구%' THEN '마포구' " +
            "    WHEN a.roadAddress LIKE '%서대문구%' THEN '서대문구' " +
            "    WHEN a.roadAddress LIKE '%서초구%' THEN '서초구' " +
            "    WHEN a.roadAddress LIKE '%성동구%' THEN '성동구' " +
            "    WHEN a.roadAddress LIKE '%성북구%' THEN '성북구' " +
            "    WHEN a.roadAddress LIKE '%송파구%' THEN '송파구' " +
            "    WHEN a.roadAddress LIKE '%양천구%' THEN '양천구' " +
            "    WHEN a.roadAddress LIKE '%영등포구%' THEN '영등포구' " +
            "    WHEN a.roadAddress LIKE '%용산구%' THEN '용산구' " +
            "    WHEN a.roadAddress LIKE '%은평구%' THEN '은평구' " +
            "    WHEN a.roadAddress LIKE '%종로구%' THEN '종로구' " +
            "    WHEN a.roadAddress LIKE '%중구%' THEN '중구' " +
            "    WHEN a.roadAddress LIKE '%중랑구%' THEN '중랑구' " +
            "    ELSE '기타' " +
            "END " +
            "ORDER BY COUNT(a.id) DESC")
    List<DistrictUniversityCountDto> findUniversityCountByDistrict();
}