package com.WhereHouse.AnalysisData.population.repository;

import com.WhereHouse.AnalysisData.population.entity.AnalysisPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisPopulationRepository extends JpaRepository<AnalysisPopulationDensity, Long> {

    Optional<AnalysisPopulationDensity> findByDistrictName(String districtName);

    List<AnalysisPopulationDensity> findAllByOrderByPopulationDensityDesc();

    boolean existsByDistrictName(String districtName);

    @Query("SELECT COUNT(a) FROM AnalysisPopulationDensity a")
    long countAnalysisData();

    @Query("SELECT a.districtName, a.populationDensity FROM AnalysisPopulationDensity a ORDER BY a.populationDensity DESC")
    List<Object[]> findDistrictDensityRanking();
}