package com.WhereHouse.APITest.ConvenienceStore.Repository;

import com.WhereHouse.APITest.ConvenienceStore.Entity.ConvenienceStoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConvenienceStoreStatisticsRepository extends JpaRepository<ConvenienceStoreStatistics, Long> {

    Optional<ConvenienceStoreStatistics> findByKakaoPlaceId(String kakaoPlaceId);

    List<ConvenienceStoreStatistics> findByDistrict(String district);

    List<ConvenienceStoreStatistics> findByStoreBrand(String storeBrand);

    List<ConvenienceStoreStatistics> findByIs24Hours(String is24Hours);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    @Query("SELECT COUNT(c) FROM ConvenienceStoreStatistics c WHERE c.district = :district")
    long countByDistrict(@Param("district") String district);

    @Query("SELECT COUNT(c) FROM ConvenienceStoreStatistics c WHERE c.district = :district AND c.is24Hours = 'Y'")
    long count24HoursByDistrict(@Param("district") String district);

    @Query("SELECT c.district, COUNT(c) FROM ConvenienceStoreStatistics c GROUP BY c.district ORDER BY COUNT(c) DESC")
    List<Object[]> countStoresByDistrict();

    @Query("SELECT c.storeBrand, COUNT(c) FROM ConvenienceStoreStatistics c GROUP BY c.storeBrand ORDER BY COUNT(c) DESC")
    List<Object[]> countStoresByBrand();
}