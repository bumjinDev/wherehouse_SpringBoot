package com.WhereHouse.AnalysisStaticData.Population.repository;

import com.WhereHouse.AnalysisStaticData.Population.entity.PopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PopulationDensityRepository extends JpaRepository<PopulationDensity, Long> {

    // 구 이름과 연도로 조회
    Optional<PopulationDensity> findByDistrictNameAndYear(String districtName, Integer year);

    // 인구밀도 순으로 정렬 (내림차순) - '소계' 제외
    @Query("SELECT a FROM PopulationDensity a WHERE a.districtName != '소계' ORDER BY a.populationDensity DESC")
    List<PopulationDensity> findAllOrderByPopulationDensityDesc();

    // 인구수 순으로 정렬 (내림차순) - '소계' 제외
    @Query("SELECT a FROM PopulationDensity a WHERE a.districtName != '소계' ORDER BY a.populationCount DESC")
    List<PopulationDensity> findAllOrderByPopulationCountDesc();

    // 면적 순으로 정렬 (내림차순) - '소계' 제외
    @Query("SELECT a FROM PopulationDensity a WHERE a.districtName != '소계' ORDER BY a.areaSize DESC")
    List<PopulationDensity> findAllOrderByAreaSizeDesc();

    // 구 이름 검색
    @Query("SELECT a FROM PopulationDensity a WHERE a.districtName LIKE %:keyword% AND a.districtName != '소계'")
    List<PopulationDensity> findByDistrictNameContaining(@Param("keyword") String keyword);

    // 특정 인구밀도 이상인 구
    @Query("SELECT a FROM PopulationDensity a WHERE a.populationDensity >= :density AND a.districtName != '소계' ORDER BY a.populationDensity DESC")
    List<PopulationDensity> findByPopulationDensityGreaterThanEqual(@Param("density") BigDecimal density);

    // 특정 인구수 이상인 구
    @Query("SELECT a FROM PopulationDensity a WHERE a.populationCount >= :population AND a.districtName != '소계' ORDER BY a.populationCount DESC")
    List<PopulationDensity> findByPopulationCountGreaterThanEqual(@Param("population") Long population);

    // 연도별 조회
    List<PopulationDensity> findByYear(Integer year);

    // 중복 체크
    boolean existsByDistrictNameAndYear(String districtName, Integer year);

    // 통계 쿼리
    @Query("SELECT COUNT(a) FROM PopulationDensity a WHERE a.districtName != '소계'")
    long countValidDistricts();

    @Query("SELECT SUM(a.populationCount) FROM PopulationDensity a WHERE a.districtName != '소계'")
    Long getTotalPopulation();

    @Query("SELECT SUM(a.areaSize) FROM PopulationDensity a WHERE a.districtName != '소계'")
    BigDecimal getTotalAreaSize();

    @Query("SELECT AVG(a.populationDensity) FROM PopulationDensity a WHERE a.districtName != '소계'")
    BigDecimal getAveragePopulationDensity();

    @Query("SELECT MAX(a.populationDensity) FROM PopulationDensity a WHERE a.districtName != '소계'")
    BigDecimal getMaxPopulationDensity();

    @Query("SELECT MIN(a.populationDensity) FROM PopulationDensity a WHERE a.districtName != '소계'")
    BigDecimal getMinPopulationDensity();

    // 상위 N개 구 조회
    @Query("SELECT a FROM PopulationDensity a WHERE a.districtName != '소계' ORDER BY a.populationDensity DESC")
    List<PopulationDensity> findTopByPopulationDensity();
}