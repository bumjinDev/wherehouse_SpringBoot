package com.wherehouse.AnalysisData.cinema.repository;

import com.wherehouse.AnalysisData.cinema.dto.DistrictCinemaCountDto;
import com.wherehouse.AnalysisData.cinema.entity.AnalysisCinemaStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisCinemaRepository extends JpaRepository<AnalysisCinemaStatistics, Long> {

    /**
     * 서울시 자치구별 영화관 수를 계산하여 DTO 리스트로 반환합니다.
     * jibunAddress를 기준으로 그룹화합니다.
     * @return DistrictCinemaCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.cinema.dto.DistrictCinemaCountDto(a.jibunAddress, COUNT(a)) " +
            "FROM AnalysisCinemaStatistics a " +
            "WHERE a.jibunAddress IS NOT NULL " +
            "GROUP BY a.jibunAddress " +
            "ORDER BY COUNT(a) DESC")
    List<DistrictCinemaCountDto> findCinemaCountByDistrict();
}