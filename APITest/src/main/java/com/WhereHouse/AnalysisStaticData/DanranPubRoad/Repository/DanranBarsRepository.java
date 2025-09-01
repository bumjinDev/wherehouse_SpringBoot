package com.WhereHouse.APITest.DanranPubRoad.Repository;

import com.WhereHouse.APITest.DanranPubRoad.Entity.DanranBars;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DanranBarsRepository extends JpaRepository<DanranBars, Long> {

    List<DanranBars> findByDistrictCodeAndBusinessStatusCode(String districtCode, String businessStatusCode);

    @Query("SELECT COUNT(d) FROM DanranBars d WHERE d.districtCode = :districtCode AND d.businessStatusCode = '1'")
    Long countActiveByDistrictCode(@Param("districtCode") String districtCode);

    @Query("SELECT d.businessCategory, COUNT(d) FROM DanranBars d WHERE d.businessStatusCode = '1' GROUP BY d.businessCategory")
    List<Object[]> countByBusinessCategory();

    boolean existsByManagementNumber(String managementNumber);
}