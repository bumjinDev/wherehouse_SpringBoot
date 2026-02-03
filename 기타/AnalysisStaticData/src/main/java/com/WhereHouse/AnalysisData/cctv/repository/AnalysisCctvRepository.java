package com.WhereHouse.AnalysisData.cctv.repository;

import com.WhereHouse.AnalysisData.cctv.entity.AnalysisCctvStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisCctvRepository extends JpaRepository<AnalysisCctvStatistics, Long> {

    Optional<AnalysisCctvStatistics> findByRoadAddress(String roadAddress);

    List<AnalysisCctvStatistics> findByManagementAgency(String managementAgency);

    boolean existsByRoadAddress(String roadAddress);

    @Query("SELECT COUNT(a) FROM AnalysisCctvStatistics a")
    long countAnalysisData();

    @Query("SELECT COUNT(a) FROM AnalysisCctvStatistics a WHERE a.managementAgency = :agency")
    long countByManagementAgency(@Param("agency") String agency);

    @Query("SELECT a.managementAgency, COUNT(a) FROM AnalysisCctvStatistics a GROUP BY a.managementAgency ORDER BY COUNT(a) DESC")
    List<Object[]> findAgencyCctvDensityRanking();

    @Query(value = "SELECT SUBSTR(ROAD_ADDRESS, 1, INSTR(ROAD_ADDRESS, ' ', 1, 2) - 1) as district, COUNT(*) as cctv_count " +
            "FROM ANALYSIS_CCTV_STATISTICS " +
            "WHERE ROAD_ADDRESS LIKE '서울%' " +
            "GROUP BY SUBSTR(ROAD_ADDRESS, 1, INSTR(ROAD_ADDRESS, ' ', 1, 2) - 1) " +
            "ORDER BY cctv_count DESC", nativeQuery = true)
    List<Object[]> findDistrictCctvDensityRanking();
}