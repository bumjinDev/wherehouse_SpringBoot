package com.WhereHouse.APITest.CriminalInfoSave.repository;

import com.WhereHouse.APITest.CriminalInfoSave.entity.CrimeStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CrimeStatisticsRepository extends JpaRepository<CrimeStatistics, Long> {
    Optional<CrimeStatistics> findByDistrictName(String districtName);
    List<CrimeStatistics> findAllByOrderByTotalOccurrenceDesc();
    boolean existsByDistrictName(String districtName);
}