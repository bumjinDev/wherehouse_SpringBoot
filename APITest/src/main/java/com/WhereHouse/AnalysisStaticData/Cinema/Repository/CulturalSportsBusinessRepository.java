package com.WhereHouse.APITest.Cinema.Repository;

import com.WhereHouse.APITest.Cinema.Entity.CulturalSportsBusiness;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CulturalSportsBusinessRepository extends JpaRepository<CulturalSportsBusiness, Long> {
    List<CulturalSportsBusiness> findByBusinessStatusName(String businessStatusName);
    List<CulturalSportsBusiness> findByCultureSportsTypeName(String cultureSportsTypeName);
    Optional<CulturalSportsBusiness> findByManagementNo(String managementNo);
    boolean existsByManagementNo(String managementNo);

    @Query("SELECT c FROM CulturalSportsBusiness c WHERE c.businessName LIKE %:name%")
    List<CulturalSportsBusiness> findByBusinessNameContaining(@Param("name") String name);

    @Query("SELECT c FROM CulturalSportsBusiness c WHERE c.coordX BETWEEN :minX AND :maxX AND c.coordY BETWEEN :minY AND :maxY")
    List<CulturalSportsBusiness> findByLocationBounds(@Param("minX") Double minX, @Param("maxX") Double maxX,
                                                      @Param("minY") Double minY, @Param("maxY") Double maxY);

    @Query("SELECT COUNT(c) FROM CulturalSportsBusiness c WHERE c.businessStatusName = :statusName")
    Long countByBusinessStatus(@Param("statusName") String statusName);

    @Query("SELECT COUNT(c) FROM CulturalSportsBusiness c WHERE c.cultureSportsTypeName = :typeName")
    Long countByCultureSportsType(@Param("typeName") String typeName);
}