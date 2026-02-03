package com.WhereHouse.AnalysisData.streetlight.repository;

import com.WhereHouse.AnalysisData.streetlight.entity.AnalysisStreetlightStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisStreetlightRepository extends JpaRepository<AnalysisStreetlightStatistics, Long> {

    // 기본 CRUD는 JpaRepository가 제공

    // 데이터 검증용 커스텀 쿼리
    @Query("SELECT COUNT(a) FROM AnalysisStreetlightStatistics a")
    long countAnalysisData();

    // 구별 가로등 개수 조회 (피어슨 상관분석용)
    @Query("SELECT a.districtName, COUNT(a) FROM AnalysisStreetlightStatistics a GROUP BY a.districtName ORDER BY COUNT(a) DESC")
    List<Object[]> findStreetlightCountByDistrict();

    // 특정 구의 가로등 목록 조회
    List<AnalysisStreetlightStatistics> findByDistrictName(String districtName);

    // 관리번호로 중복 확인
    boolean existsByManagementNumber(String managementNumber);
}