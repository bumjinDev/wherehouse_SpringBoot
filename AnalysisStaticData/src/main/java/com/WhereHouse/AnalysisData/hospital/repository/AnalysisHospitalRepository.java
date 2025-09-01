package com.WhereHouse.AnalysisData.hospital.repository;

import com.WhereHouse.AnalysisData.hospital.entity.AnalysisHospitalStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisHospitalRepository extends JpaRepository<AnalysisHospitalStatistics, Long> {

    // 기본 CRUD는 JpaRepository가 제공

    // 데이터 검증용 커스텀 쿼리
    @Query("SELECT COUNT(a) FROM AnalysisHospitalStatistics a")
    long countAnalysisData();

    // 구별 병원 개수 조회 (피어슨 상관분석용)
    @Query("SELECT a.districtName, COUNT(a) FROM AnalysisHospitalStatistics a GROUP BY a.districtName ORDER BY COUNT(a) DESC")
    List<Object[]> findHospitalCountByDistrict();

    // 특정 구의 병원 목록 조회
    List<AnalysisHospitalStatistics> findByDistrictName(String districtName);

    // 병원명으로 중복 확인
    boolean existsByHospitalNameAndAddress(String hospitalName, String address);
}