package com.WhereHouse.AnalysisData.subway.repository;

import com.WhereHouse.AnalysisData.subway.entity.AnalysisSubwayStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AnalysisSubwayRepository extends JpaRepository<AnalysisSubwayStation, Long> {

    List<AnalysisSubwayStation> findByStationNameKor(String stationNameKor);

    List<AnalysisSubwayStation> findByStationNameKorContaining(String stationName);

    boolean existsByStationNameKor(String stationNameKor);

    @Query("SELECT COUNT(a) FROM AnalysisSubwayStation a")
    long countAnalysisData();

    @Query("SELECT a FROM AnalysisSubwayStation a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    List<AnalysisSubwayStation> findAllWithCoordinates();

    @Query("SELECT COUNT(a) FROM AnalysisSubwayStation a WHERE a.latitude IS NULL OR a.longitude IS NULL")
    long countMissingCoordinates();

    @Query("SELECT a FROM AnalysisSubwayStation a WHERE a.stationNameKor LIKE %:name%")
    List<AnalysisSubwayStation> findByStationNameContaining(@Param("name") String name);

    @Query("SELECT a FROM AnalysisSubwayStation a WHERE a.roadAddress LIKE %:address% OR a.jibunAddress LIKE %:address%")
    List<AnalysisSubwayStation> findByAddressContaining(@Param("address") String address);
}