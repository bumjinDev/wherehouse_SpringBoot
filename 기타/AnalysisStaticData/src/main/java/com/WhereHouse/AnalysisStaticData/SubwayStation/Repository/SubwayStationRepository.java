package com.WhereHouse.AnalysisStaticData.SubwayStation.Repository;

import com.WhereHouse.AnalysisStaticData.SubwayStation.Entity.SubwayStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubwayStationRepository extends JpaRepository<SubwayStation, Long> {

    Optional<SubwayStation> findByStationNumber(String stationNumber);

    Optional<SubwayStation> findByStationNumberAndLineNumber(String stationNumber, String lineNumber);

    List<SubwayStation> findByStationNameKorContaining(String stationName);

    List<SubwayStation> findByLineNumber(String lineNumber);

    List<SubwayStation> findByLineNumberOrderBySeqNo(String lineNumber);

    @Query("SELECT DISTINCT s.lineNumber FROM SubwayStation s ORDER BY CAST(s.lineNumber AS int)")
    List<String> findDistinctLineNumbers();

    @Query("SELECT s FROM SubwayStation s WHERE s.stationNameKor LIKE %:keyword% OR s.roadAddress LIKE %:keyword% OR s.jibunAddress LIKE %:keyword%")
    List<SubwayStation> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT s.lineNumber, COUNT(s) FROM SubwayStation s GROUP BY s.lineNumber ORDER BY CAST(s.lineNumber AS int)")
    List<Object[]> countStationsByLine();

    boolean existsByStationNumberAndLineNumber(String stationNumber, String lineNumber);
}