package com.WhereHouse.AnalysisData.pcbang.repository;

import com.WhereHouse.AnalysisData.pcbang.entity.AnalysisPcBangStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisPcBangRepository extends JpaRepository<AnalysisPcBangStatistics, Long> {

    Optional<AnalysisPcBangStatistics> findByManagementNumber(String managementNumber);

    List<AnalysisPcBangStatistics> findByDistrictCode(String districtCode);

    List<AnalysisPcBangStatistics> findByBusinessStatusName(String businessStatusName);

    boolean existsByManagementNumber(String managementNumber);

    @Query("SELECT COUNT(a) FROM AnalysisPcBangStatistics a")
    long countAnalysisData();

    @Query("SELECT COUNT(a) FROM AnalysisPcBangStatistics a WHERE a.districtCode = :districtCode")
    long countByDistrictCode(@Param("districtCode") String districtCode);

    @Query("SELECT a.districtCode, COUNT(a) FROM AnalysisPcBangStatistics a GROUP BY a.districtCode ORDER BY COUNT(a) DESC")
    List<Object[]> findDistrictPcBangDensityRanking();

    @Query("SELECT a.businessStatusName, COUNT(a) FROM AnalysisPcBangStatistics a GROUP BY a.businessStatusName ORDER BY COUNT(a) DESC")
    List<Object[]> findBusinessStatusDistribution();

    @Query(value = "SELECT SUBSTR(ROAD_ADDRESS, 1, INSTR(ROAD_ADDRESS, ' ', 1, 2) - 1) as district, COUNT(*) as pc_bang_count " +
            "FROM ANALYSIS_PC_BANG_STATISTICS " +
            "WHERE ROAD_ADDRESS LIKE '서울%' " +
            "GROUP BY SUBSTR(ROAD_ADDRESS, 1, INSTR(ROAD_ADDRESS, ' ', 1, 2) - 1) " +
            "ORDER BY pc_bang_count DESC", nativeQuery = true)
    List<Object[]> findSeoulDistrictPcBangDensityRanking();
}