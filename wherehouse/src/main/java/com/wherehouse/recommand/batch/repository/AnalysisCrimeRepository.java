package com.wherehouse.AnalysisData.crime.repository;

import com.wherehouse.AnalysisData.crime.dto.DistrictCrimeCountDto;
import com.wherehouse.AnalysisData.crime.entity.AnalysisCrimeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisCrimeRepository extends JpaRepository<AnalysisCrimeStatistics, Long> {

    /**
     * 서울시 자치구별 총 범죄 발생 건수를 합산하여 DTO 리스트로 반환합니다.
     * @return DistrictCrimeCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.crime.dto.DistrictCrimeCountDto" +
            "(a.districtName, a.totalOccurrence, a.totalArrest, a.totalArrest, a.murderOccurrence, a.murderArrest, a.robberyArrest, a.sexualCrimeOccurrence, a.sexualCrimeArrest, a.theftOccurrence, a.theftArrest, a.violenceOccurrence, a.violenceArrest) " +
            "FROM AnalysisCrimeStatistics a ")
    List<DistrictCrimeCountDto> findCrimeCount();
}