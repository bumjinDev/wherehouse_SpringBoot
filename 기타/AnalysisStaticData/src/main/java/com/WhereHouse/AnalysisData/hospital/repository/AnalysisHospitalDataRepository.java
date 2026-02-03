package com.WhereHouse.AnalysisData.hospital.repository;

import com.WhereHouse.AnalysisData.hospital.entity.AnalysisHospitalData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisHospitalDataRepository extends JpaRepository<AnalysisHospitalData, Long> {

    /**
     * 좌표 완성도 확인
     */
    @Query("SELECT COUNT(*) FROM AnalysisHospitalData")
    Long getTotalCount();

    @Query("SELECT COUNT(*) FROM AnalysisHospitalData a WHERE a.latitude IS NOT NULL AND a.longitude IS NOT NULL")
    Long getCoordinateCount();

    /**
     * 업종별 분포 조회
     */
    @Query("SELECT a.businessTypeName, COUNT(a) FROM AnalysisHospitalData a GROUP BY a.businessTypeName ORDER BY COUNT(a) DESC")
    List<Object[]> countByBusinessType();

    /**
     * 상세영업상태별 분포 조회
     */
    @Query("SELECT a.detailedStatusName, COUNT(a) FROM AnalysisHospitalData a GROUP BY a.detailedStatusName ORDER BY COUNT(a) DESC")
    List<Object[]> countByDetailedStatus();

    /**
     * 데이터 품질 검증용 통계 조회
     */
    @Query("SELECT " +
            "COUNT(*) as totalCount, " +
            "COUNT(a.latitude) as coordinateCount, " +
            "COUNT(a.businessName) as businessNameCount, " +
            "COUNT(a.businessTypeName) as businessTypeCount " +
            "FROM AnalysisHospitalData a")
    Object[] getDataQualityStats();
}