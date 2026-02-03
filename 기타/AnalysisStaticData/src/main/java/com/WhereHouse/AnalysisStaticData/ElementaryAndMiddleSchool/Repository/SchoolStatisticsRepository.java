package com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Repository;

import com.WhereHouse.AnalysisStaticData.ElementaryAndMiddleSchool.Entity.SchoolStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolStatisticsRepository extends JpaRepository<SchoolStatistics, Long> {
    Optional<SchoolStatistics> findBySchoolId(String schoolId);
    List<SchoolStatistics> findBySchoolLevel(String schoolLevel);
    List<SchoolStatistics> findByOperationStatus(String operationStatus);
    boolean existsBySchoolId(String schoolId);

    @Query("SELECT s FROM SchoolStatistics s WHERE s.educationOfficeName LIKE %:officeName%")
    List<SchoolStatistics> findByEducationOfficeNameContaining(@Param("officeName") String officeName);

    @Query("SELECT s FROM SchoolStatistics s WHERE s.latitude BETWEEN :minLat AND :maxLat AND s.longitude BETWEEN :minLon AND :maxLon")
    List<SchoolStatistics> findByLocationBounds(@Param("minLat") Double minLat, @Param("maxLat") Double maxLat,
                                                @Param("minLon") Double minLon, @Param("maxLon") Double maxLon);
}