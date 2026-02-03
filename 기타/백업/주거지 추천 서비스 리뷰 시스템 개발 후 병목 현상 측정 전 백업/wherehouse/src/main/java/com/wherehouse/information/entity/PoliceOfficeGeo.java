package com.wherehouse.information.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POLICEOFFICE_GEO 읽기 최적화 테이블 엔티티
 *
 * 역할: 실시간 서비스 조회 성능 최적화
 * 생성: 배치 프로세스(GeohashIndexingEtlProcessor)에서 생성 및 관리
 * 용도: 실시간 서비스(LocationAnalysisService)에서 조회 전용
 *
 * 테이블 구조:
 * - ADDRESS: 파출소 주소 (PK)
 * - LATITUDE: 위도
 * - LONGITUDE: 경도
 * - GEOHASH_ID: 7자리 정밀도 지오해시 ID (B-Tree 인덱스)
 *
 * 성능 최적화:
 * - geohash_id 컬럼에 B-Tree 인덱스 적용
 * - WHERE geohash_id IN (...) 쿼리로 빠른 조회
 * - 9-Block 그리드 검색 전략 사용
 *
 * 배치 처리 주기:
 * - 매일 새벽 4시 TRUNCATE 후 재생성
 * - POLICEOFFICE 원본 테이블 데이터를 변환하여 적재
 */
@Entity
@Table(name = "POLICEOFFICE_GEO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoliceOfficeGeo {

    @Id
    @Column(name = "ADDRESS", length = 255, nullable = false)
    private String address;  // 주소 (PK)

    @Column(name = "LATITUDE", nullable = false)
    private Double latitude;  // 위도

    @Column(name = "LONGITUDE", nullable = false)
    private Double longitude;  // 경도

    @Column(name = "GEOHASH_ID", length = 12, nullable = false)
    private String geohashId;  // 7자리 정밀도 지오해시 ID (B-Tree 인덱스)
}