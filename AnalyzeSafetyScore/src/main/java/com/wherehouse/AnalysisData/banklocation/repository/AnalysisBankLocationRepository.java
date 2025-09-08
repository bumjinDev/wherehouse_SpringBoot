package com.wherehouse.AnalysisData.banklocation.repository;

import com.wherehouse.AnalysisData.banklocation.dto.DistrictBankLocationCountDto;
import com.wherehouse.AnalysisData.banklocation.entity.AnalysisBankLocationStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisBankLocationRepository extends JpaRepository<AnalysisBankLocationStatistics, Long> {

    /**
     * 서울시 자치구별 은행 지점 수를 계산하여 DTO 리스트로 반환합니다.
     * @return DistrictBankLocationCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.banklocation.dto.DistrictBankLocationCountDto(a.district, COUNT(a)) " +
            "FROM AnalysisBankLocationStatistics a " +
            "WHERE a.district IS NOT NULL " +
            "GROUP BY a.district " +
            "ORDER BY COUNT(a) DESC")
    List<DistrictBankLocationCountDto> findBankLocationCountByDistrict();
}