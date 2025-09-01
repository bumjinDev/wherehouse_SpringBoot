package com.WhereHouse.APITest.StreetLightRaw.Repository;

import com.WhereHouse.APITest.StreetLightRaw.Entity.StreetlightRawData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StreetlightRawDataRepository extends JpaRepository<StreetlightRawData, Long> {
    Optional<StreetlightRawData> findByManagementNumber(String managementNumber);
    List<StreetlightRawData> findAllByOrderByManagementNumberAsc();
    boolean existsByManagementNumber(String managementNumber);

    @Query("SELECT s FROM StreetlightRawData s WHERE s.managementNumber LIKE %:prefix%")
    List<StreetlightRawData> findByManagementNumberPrefix(@Param("prefix") String prefix);

    @Query("SELECT s FROM StreetlightRawData s WHERE s.latitude BETWEEN :minLat AND :maxLat AND s.longitude BETWEEN :minLon AND :maxLon")
    List<StreetlightRawData> findByLocationBounds(@Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
                                                  @Param("minLon") Double minLon, @Param("maxLon") Double maxLon);

    @Query("SELECT COUNT(s) FROM StreetlightRawData s")
    Long countTotalStreetlights();
}