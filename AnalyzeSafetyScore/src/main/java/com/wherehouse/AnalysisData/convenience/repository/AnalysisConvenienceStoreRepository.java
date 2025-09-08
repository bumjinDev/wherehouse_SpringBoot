package com.wherehouse.AnalysisData.convenience.repository;

import com.wherehouse.AnalysisData.convenience.dto.DistrictConvenienceStoreCountDto;
import com.wherehouse.AnalysisData.convenience.entity.AnalysisConvenienceStoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisConvenienceStoreRepository extends JpaRepository<AnalysisConvenienceStoreStatistics, Long> {

    /**
     * 서울시 자치구별 편의점 수를 계산하여 DTO 리스트로 반환합니다.
     * @return DistrictConvenienceStoreCountDto 리스트
     */
    @Query("SELECT new com.wherehouse.AnalysisData.convenience.dto.DistrictConvenienceStoreCountDto(a.district, COUNT(a)) " +
            "FROM AnalysisConvenienceStoreStatistics a " +
            "WHERE a.district IS NOT NULL " +
            "GROUP BY a.district " +
            "ORDER BY COUNT(a) DESC")
    List<DistrictConvenienceStoreCountDto> findConvenienceStoreCountByDistrict();
}