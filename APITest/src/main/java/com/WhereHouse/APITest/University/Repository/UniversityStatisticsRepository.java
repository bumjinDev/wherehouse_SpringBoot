package com.WhereHouse.APITest.University.Entity.Repository;

import com.WhereHouse.APITest.University.Entity.UniversityStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UniversityStatisticsRepository extends JpaRepository<UniversityStatistics, Long> {
    List<UniversityStatistics> findBySchoolName(String schoolName);
    List<UniversityStatistics> findByUniversityType(String universityType);
    List<UniversityStatistics> findByEstablishmentType(String establishmentType);
    List<UniversityStatistics> findBySidoName(String sidoName);
    boolean existsBySchoolNameAndUniversityType(String schoolName, String universityType);

    @Query("SELECT u FROM UniversityStatistics u WHERE u.schoolName LIKE %:name%")
    List<UniversityStatistics> findBySchoolNameContaining(@Param("name") String name);

    @Query("SELECT u FROM UniversityStatistics u WHERE u.sidoName = :sidoName AND u.universityType = :universityType")
    List<UniversityStatistics> findBySidoNameAndUniversityType(@Param("sidoName") String sidoName,
                                                               @Param("universityType") String universityType);

    @Query("SELECT COUNT(u) FROM UniversityStatistics u WHERE u.sidoName = :sidoName")
    Long countBySidoName(@Param("sidoName") String sidoName);
}