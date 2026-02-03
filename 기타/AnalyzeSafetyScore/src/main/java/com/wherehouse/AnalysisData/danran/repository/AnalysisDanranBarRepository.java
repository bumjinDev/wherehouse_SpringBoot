package com.wherehouse.AnalysisData.danran.repository;

import com.wherehouse.AnalysisData.danran.dto.DistrictDanranBarCountDto;
import com.wherehouse.AnalysisData.danran.entity.AnalysisDanranBars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisDanranBarRepository extends JpaRepository<AnalysisDanranBars, Long> {

    /**
     * 서울시 자치구별 유흥주점 수를 계산하여 DTO 리스트로 반환합니다.
     * @return DistrictDanranBarCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.danran.dto.DistrictDanranBarCountDto(a.districtName, COUNT(a)) " +
            "FROM AnalysisDanranBars a " +
            "WHERE a.districtName IS NOT NULL " +
            "GROUP BY a.districtName " +
            "ORDER BY COUNT(a) DESC")
    List<DistrictDanranBarCountDto> findDanranBarCountByDistrict();
}