package com.WhereHouse.AnalysisData.convenience.repository;

import com.WhereHouse.AnalysisData.convenience.entity.AnalysisConvenienceStoreStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisConvenienceStoreRepository extends JpaRepository<AnalysisConvenienceStoreStatistics, Long> {

    List<AnalysisConvenienceStoreStatistics> findByDistrict(String district);

    List<AnalysisConvenienceStoreStatistics> findByStoreBrand(String storeBrand);

    boolean existsByKakaoPlaceId(String kakaoPlaceId);

    @Query("SELECT COUNT(a) FROM AnalysisConvenienceStoreStatistics a")
    long countAnalysisData();

    @Query("SELECT a.district, COUNT(a) FROM AnalysisConvenienceStoreStatistics a GROUP BY a.district ORDER BY COUNT(a) DESC")
    List<Object[]> findStoreCountByDistrict();

    @Query("SELECT a FROM AnalysisConvenienceStoreStatistics a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisConvenienceStoreStatistics> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisConvenienceStoreStatistics a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a.storeBrand, COUNT(a) FROM AnalysisConvenienceStoreStatistics a GROUP BY a.storeBrand ORDER BY COUNT(a) DESC")
    List<Object[]> findStoreCountByBrand();

    @Query("SELECT a FROM AnalysisConvenienceStoreStatistics a WHERE a.placeName LIKE %:name%")
    List<AnalysisConvenienceStoreStatistics> findByPlaceNameContaining(@Param("name") String name);

    @Query("SELECT a FROM AnalysisConvenienceStoreStatistics a WHERE a.addressName LIKE %:address% OR a.roadAddressName LIKE %:address%")
    List<AnalysisConvenienceStoreStatistics> findByAddressContaining(@Param("address") String address);
}