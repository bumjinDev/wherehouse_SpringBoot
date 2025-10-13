package com.wherehouse.information.dao;


import com.wherehouse.information.entity.PoliceOfficeGeo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * PoliceOfficeGeo 테이블 접근을 위한 JPA Repository
 *
 * 주요 기능:
 * - Geohash ID 기반 9-Block 그리드 검색
 * - B-Tree 인덱스를 활용한 빠른 조회
 *
 * 사용 위치:
 * - LocationAnalysisServiceImpl: 가장 가까운 파출소 조회
 *
 * 성능 최적화:
 * - WHERE geohash_id IN (...) 쿼리로 B-Tree 인덱스 활용
 * - 9개 격자 범위를 한 번의 쿼리로 조회
 */
@Repository
public interface PoliceOfficeGeoRepository extends JpaRepository<PoliceOfficeGeo, String> {

    /**
     * 9-Block 그리드 범위 내 모든 파출소 조회
     *
     * @param geohashIds 9개의 Geohash ID 목록 (중심 1개 + 인접 8개)
     * @return 해당 격자들에 속한 모든 파출소 목록
     *
     * 쿼리 실행 예시:
     * SELECT * FROM POLICEOFFICE_GEO
     * WHERE GEOHASH_ID IN ('wydm7p1', 'wydm7p2', 'wydm7p3', 'wydm7p4', 'wydm7p5',
     *                      'wydm7p6', 'wydm7p0', 'wydm7nx', 'wydm7nz')
     *
     * 인덱스 활용:
     * - IDX_POLICE_GEO_GEOHASH (B-Tree 인덱스) 사용
     * - 9번의 Index Scan으로 빠른 조회
     *
     * 주의사항:
     * - 조회된 결과는 9개 격자 전체 범위이므로,
     *   애플리케이션 레이어에서 가장 가까운 파출소 계산 필요
     */
    @Query("SELECT p FROM PoliceOfficeGeo p WHERE p.geohashId IN :geohashIds")
    List<PoliceOfficeGeo> findByGeohashIdIn(@Param("geohashIds") List<String> geohashIds);
}