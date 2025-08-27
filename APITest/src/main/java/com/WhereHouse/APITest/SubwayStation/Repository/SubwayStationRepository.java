package com.WhereHouse.APITest.SubwayStation.Repository;

import com.WhereHouse.APITest.SubwayStation.Entity.SubwayStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubwayStationRepository extends JpaRepository<SubwayStation, String> {

    Optional<SubwayStation> findByStationCode(String stationCode);

    List<SubwayStation> findByStationNameKorContaining(String stationName);

    List<SubwayStation> findByLineNumber(String lineNumber);

    @Query("SELECT s FROM SubwayStation s WHERE s.lineNumber = :lineNumber ORDER BY s.externalCode")
    List<SubwayStation> findByLineNumberOrderByExternalCode(@Param("lineNumber") String lineNumber);

    @Query("SELECT DISTINCT s.lineNumber FROM SubwayStation s ORDER BY s.lineNumber")
    List<String> findDistinctLineNumbers();

    boolean existsByStationCode(String stationCode);
}