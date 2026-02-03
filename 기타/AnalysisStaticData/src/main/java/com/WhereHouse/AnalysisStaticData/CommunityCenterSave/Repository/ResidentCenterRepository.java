package com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Repository;

import com.WhereHouse.AnalysisStaticData.CommunityCenterSave.Entity.ResidentCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResidentCenterRepository extends JpaRepository<ResidentCenter, Long> {

    Optional<ResidentCenter> findBySerialNo(Integer serialNo);
    List<ResidentCenter> findBySigungu(String sigungu);
    boolean existsBySerialNo(Integer serialNo);
}