package com.wherehouse.AnalysisData.cctv.repository;

import com.wherehouse.AnalysisData.cctv.dto.DistrictCctvCountDto;
import com.wherehouse.AnalysisData.cctv.entity.AnalysisCctvStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisCctvRepository extends JpaRepository<AnalysisCctvStatistics, Long> {

    /**
     * 서울시 자치구별 CCTV 수를 계산하여 DTO 리스트로 반환합니다.
     * @return DistrictCctvCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.cctv.dto.DistrictCctvCountDto(a.managementAgency, COUNT(a)) " +
            "FROM AnalysisCctvStatistics a " +
            "WHERE a.managementAgency IS NOT NULL " +
            "GROUP BY a.managementAgency " +
            "ORDER BY COUNT(a) DESC")
    List<DistrictCctvCountDto> findCctvCountByDistrict();
}