package com.wherehouse.information.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ARRESTRATE 검거율 테이블 엔티티
 *
 * 역할: 서울시 구별 검거율 데이터 저장
 * 용도: 실시간 서비스에서 조회 전용
 *
 * 테이블 구조:
 * - ADDR: 서울시 구 이름 (PK) - 예: "종로구", "중구"
 * - RATE: 검거율 (0.0 ~ 1.0 사이의 실수)
 *
 * 데이터 출처:
 * - 빅데이터 분석 보고서 (4조_2팀)
 * - 5대 범죄 검거 수 / 5대 범죄 발생 건수
 * - 회귀 분석 결과 (Adjusted R²: 0.5097, p-value: 0.0005745)
 *
 * 사용 예시:
 * - 주소 "서울특별시 중구 세종대로 110" → "중구" 추출 → 검거율 조회
 */
@Entity
@Table(name = "ARRESTRATE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArrestRate {

    @Id
    @Column(name = "ADDR", length = 50, nullable = false)
    private String addr;  // 서울시 구 이름 (PK)

    @Column(name = "RATE", nullable = false)
    private Double rate;  // 검거율 (0.0 ~ 1.0)
}