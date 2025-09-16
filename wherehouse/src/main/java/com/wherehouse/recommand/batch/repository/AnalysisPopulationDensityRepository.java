package com.wherehouse.AnalysisData.population.repository;

import com.wherehouse.AnalysisData.population.entity.AnalysisPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisPopulationDensityRepository extends JpaRepository<AnalysisPopulationDensity, Long> {

    /**
     * 1번째 메서드: 서울시 자치구별 인구밀도를 반환합니다 (최신년도 기준)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 인구밀도(BigDecimal)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    p.DISTRICT_NAME, " +
            "    p.POPULATION_DENSITY " +
            "FROM ANALYSIS_POPULATION_DENSITY p " +
            "WHERE p.YEAR = (SELECT MAX(YEAR) FROM ANALYSIS_POPULATION_DENSITY) " +
            "ORDER BY p.POPULATION_DENSITY DESC",
            nativeQuery = true)
    List<Object[]> findPopulationDensityByDistrict();

    /**
     * 2번째 메서드: 서울시 자치구별 인구수를 반환합니다 (최신년도 기준)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 인구수(Number)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    p.DISTRICT_NAME, " +
            "    p.POPULATION_COUNT " +
            "FROM ANALYSIS_POPULATION_DENSITY p " +
            "WHERE p.YEAR = (SELECT MAX(YEAR) FROM ANALYSIS_POPULATION_DENSITY) " +
            "ORDER BY p.POPULATION_COUNT DESC",
            nativeQuery = true)
    List<Object[]> findPopulationCountByDistrict();

    /**
     * 3번째 메서드: 서울시 자치구별 면적을 반환합니다 (최신년도 기준)
     * @return List<Object[]>: 각 Object[]는 [자치구명(String), 면적(BigDecimal)]로 구성됩니다.
     */
    @Query(value = "SELECT " +
            "    p.DISTRICT_NAME, " +
            "    p.AREA_SIZE " +
            "FROM ANALYSIS_POPULATION_DENSITY p " +
            "WHERE p.YEAR = (SELECT MAX(YEAR) FROM ANALYSIS_POPULATION_DENSITY) " +
            "ORDER BY p.AREA_SIZE DESC",
            nativeQuery = true)
    List<Object[]> findAreaSizeByDistrict();
}