package com.WhereHouse.AnalysisStaticData.HospitalSave.repository;

import com.WhereHouse.AnalysisStaticData.HospitalSave.entity.HospitalData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HospitalDataRepository extends JpaRepository<HospitalData, Long> {

    @Query("SELECT h.businessStatusName, COUNT(h) FROM HospitalData h GROUP BY h.businessStatusName")
    List<Object[]> countByBusinessStatus();
}