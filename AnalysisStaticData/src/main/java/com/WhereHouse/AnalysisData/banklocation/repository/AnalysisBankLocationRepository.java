package com.WhereHouse.AnalysisData.banklocation.repository;

import com.WhereHouse.AnalysisData.banklocation.entity.AnalysisBankLocationStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisBankLocationRepository extends JpaRepository<AnalysisBankLocationStatistics, Long> {

    Optional<AnalysisBankLocationStatistics> findByKakaoPlaceId(String kakaoPlaceId);

    List<AnalysisBankLocationStatistics> findByDistrict(String district);

    List<AnalysisBankLocationStatistics> findByBankBrand(String bankBrand);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    @Query("SELECT COUNT(a) FROM AnalysisBankLocationStatistics a")
    long countAnalysisData();

    @Query("SELECT COUNT(a) FROM AnalysisBankLocationStatistics a WHERE a.district = :district")
    long countByDistrict(@Param("district") String district);

    @Query("SELECT a.district, COUNT(a) FROM AnalysisBankLocationStatistics a GROUP BY a.district ORDER BY COUNT(a) DESC")
    List<Object[]> findDistrictBankDensityRanking();

    @Query("SELECT a.bankBrand, COUNT(a) FROM AnalysisBankLocationStatistics a WHERE a.bankBrand IS NOT NULL GROUP BY a.bankBrand ORDER BY COUNT(a) DESC")
    List<Object[]> findBankBrandDistribution();
}