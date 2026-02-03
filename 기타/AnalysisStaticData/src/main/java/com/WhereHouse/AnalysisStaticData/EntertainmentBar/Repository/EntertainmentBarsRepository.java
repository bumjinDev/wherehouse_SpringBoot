package com.WhereHouse.AnalysisStaticData.EntertainmentBar.Repository;

import com.WhereHouse.AnalysisStaticData.EntertainmentBar.Entity.EntertainmentBars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EntertainmentBarsRepository extends JpaRepository<EntertainmentBars, Long> {

    List<EntertainmentBars> findByDistrictCodeAndBusinessStatusCode(String districtCode, String businessStatusCode);

    @Query("SELECT COUNT(e) FROM EntertainmentBars e WHERE e.districtCode = :districtCode AND e.businessStatusCode = '1'")
    Long countActiveByDistrictCode(@Param("districtCode") String districtCode);

    @Query("SELECT e.businessCategory, COUNT(e) FROM EntertainmentBars e WHERE e.businessStatusCode = '1' GROUP BY e.businessCategory")
    List<Object[]> countByBusinessCategory();

    boolean existsByManagementNumber(String managementNumber);
}