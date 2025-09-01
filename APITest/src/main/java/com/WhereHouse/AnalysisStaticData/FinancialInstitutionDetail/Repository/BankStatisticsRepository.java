package com.WhereHouse.APITest.FinancialInstitutionDetail.Repository;

import com.WhereHouse.APITest.FinancialInstitutionDetail.Entity.BankStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankStatisticsRepository extends JpaRepository<BankStatistics, Long> {

    Optional<BankStatistics> findByKakaoPlaceId(String kakaoPlaceId);

    List<BankStatistics> findByDistrict(String district);

    List<BankStatistics> findByBankBrand(String bankBrand);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    @Query("SELECT COUNT(b) FROM BankStatistics b WHERE b.district = :district")
    long countByDistrict(@Param("district") String district);

    @Query("SELECT b.district, COUNT(b) FROM BankStatistics b GROUP BY b.district ORDER BY COUNT(b) DESC")
    List<Object[]> countBanksByDistrict();
}