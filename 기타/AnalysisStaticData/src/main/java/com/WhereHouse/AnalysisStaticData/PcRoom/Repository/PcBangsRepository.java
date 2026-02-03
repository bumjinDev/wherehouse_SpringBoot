package com.WhereHouse.AnalysisStaticData.PcRoom.Repository;

import com.WhereHouse.AnalysisStaticData.PcRoom.Entity.PcBangs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PcBangsRepository extends JpaRepository<PcBangs, Long> {

    List<PcBangs> findByDistrictCodeAndBusinessStatusCode(String districtCode, String businessStatusCode);

    @Query("SELECT COUNT(p) FROM PcBangs p WHERE p.districtCode = :districtCode AND p.businessStatusCode = '1'")
    Long countActiveByDistrictCode(@Param("districtCode") String districtCode);

    @Query("SELECT p.businessCategory, COUNT(p) FROM PcBangs p WHERE p.businessStatusCode = '1' GROUP BY p.businessCategory")
    List<Object[]> countByBusinessCategory();

    boolean existsByManagementNumber(String managementNumber);
}
