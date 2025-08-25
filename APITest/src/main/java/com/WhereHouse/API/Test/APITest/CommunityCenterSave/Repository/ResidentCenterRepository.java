package com.WhereHouse.API.Test.APITest.CommunityCenterRoad.Repository;

import com.WhereHouse.API.Test.APITest.CommunityCenterRoad.Entity.ResidentCenter;
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