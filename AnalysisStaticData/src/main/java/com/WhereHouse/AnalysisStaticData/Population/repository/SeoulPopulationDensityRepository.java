package com.WhereHouse.AnalysisStaticData.Population.repository;

import com.WhereHouse.AnalysisStaticData.Population.entity.SeoulPopulationDensity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeoulPopulationDensityRepository extends JpaRepository<SeoulPopulationDensity, Long> {

    Optional<SeoulPopulationDensity> findByDistrictLevel2(String districtName);

    @Query("SELECT s FROM SeoulPopulationDensity s WHERE s.districtLevel2 != '소계' ORDER BY s.populationDensity DESC")
    List<SeoulPopulationDensity> findAllOrderByPopulationDensityDesc();

    @Query("SELECT s FROM SeoulPopulationDensity s WHERE s.districtLevel2 LIKE %:keyword%")
    List<SeoulPopulationDensity> findByDistrictNameContaining(@Param("keyword") String keyword);

    boolean existsByDistrictLevel2(String districtName);
}