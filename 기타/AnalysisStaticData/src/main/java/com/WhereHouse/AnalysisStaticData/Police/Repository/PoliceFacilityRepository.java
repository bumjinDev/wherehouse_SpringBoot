package com.WhereHouse.AnalysisStaticData.Police.Repository;

import com.WhereHouse.AnalysisStaticData.Police.Entity.PoliceFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PoliceFacilityRepository extends JpaRepository<PoliceFacility, Long> {
    List<PoliceFacility> findByFacilityType(String facilityType);
    List<PoliceFacility> findByPoliceStation(String policeStation);
    List<PoliceFacility> findByCityProvince(String cityProvince);

    @Query("SELECT p FROM PoliceFacility p WHERE p.facilityName LIKE %:name%")
    List<PoliceFacility> findByFacilityNameContaining(@Param("name") String name);

    @Query("SELECT p FROM PoliceFacility p WHERE p.coordX BETWEEN :minX AND :maxX AND p.coordY BETWEEN :minY AND :maxY")
    List<PoliceFacility> findByLocationBounds(@Param("minX") Double minX, @Param("maxX") Double maxX,
                                              @Param("minY") Double minY, @Param("maxY") Double maxY);

    @Query("SELECT COUNT(p) FROM PoliceFacility p WHERE p.facilityType = :type")
    Long countByFacilityType(@Param("type") String type);

    @Query("SELECT COUNT(p) FROM PoliceFacility p WHERE p.policeStation = :station")
    Long countByPoliceStation(@Param("station") String station);

    // 서울시 경찰시설만 조회
    @Query("SELECT p FROM PoliceFacility p WHERE p.cityProvince LIKE '%서울%'")
    List<PoliceFacility> findSeoulPoliceFacilities();

    // 지구대만 조회
    @Query("SELECT p FROM PoliceFacility p WHERE p.facilityType = '지구대'")
    List<PoliceFacility> findAllDistrictOffices();

    // 파출소만 조회
    @Query("SELECT p FROM PoliceFacility p WHERE p.facilityType = '파출소'")
    List<PoliceFacility> findAllPoliceBoxes();
}