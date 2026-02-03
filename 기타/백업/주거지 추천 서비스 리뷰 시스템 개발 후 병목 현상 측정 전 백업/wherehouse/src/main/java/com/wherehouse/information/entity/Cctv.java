package com.wherehouse.information.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * CCTV 원본 테이블 엔티티
 *
 * 역할: Source of Truth (데이터의 최초 출처)
 * 용도: 배치 프로세스에서 읽기 전용으로 사용
 *
 * 테이블 구조:
 * - NUMBERS: CCTV 관리 번호 (PK)
 * - ADDRESS: 설치 주소
 * - LATITUDE: 위도
 * - LONGITUDE: 경도
 * - CAMERACOUNT: 카메라 대수
 *
 * 주의사항:
 * - 이 테이블은 배치 프로세스에서 수정하지 않음
 * - CCTV_GEO 테이블로 변환되어 실시간 서비스에서 사용됨
 */
@Entity
@Table(name = "CCTV")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cctv {

    @Id
    @Column(name = "NUMBERS", nullable = false)
    private Long numbers;  // CCTV 관리 번호 (PK)

    @Column(name = "ADDRESS", length = 255)
    private String address;  // 주소

    @Column(name = "LATITUDE", nullable = false)
    private Double latitude;  // 위도

    @Column(name = "LONGITUDE", nullable = false)
    private Double longitude;  // 경도

    @Column(name = "CAMERACOUNT")
    private Integer cameraCount;  // 카메라 대수
}