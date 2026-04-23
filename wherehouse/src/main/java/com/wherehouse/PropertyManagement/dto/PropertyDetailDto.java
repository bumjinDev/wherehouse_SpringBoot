package com.wherehouse.PropertyManagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F004 매물 상세 조회 응답 DTO (설계 명세서 섹션 7.6.2).
 *
 * GET /api/v1/properties/{propertyId} 의 200 OK 응답 본문.
 *
 * ────────────────────────────────────────────────────────────
 * 설계 결정 — Composition (has-a) 기반 구조
 * ────────────────────────────────────────────────────────────
 * Summary 필드들을 본 DTO 내에 재선언하지 않고 PropertySummaryDto 를 멤버로 포함.
 * 상속(is-a) 대신 합성(has-a)을 선택한 근거:
 *
 *   1) 매핑 로직 재사용
 *      목록 조회·상세 조회가 동일 매물 데이터에서 분기되므로,
 *      Summary 매핑 로직을 한 번 작성하면 Detail 구성 시 재사용 가능.
 *
 *   2) Summary 필드 변경 시 자동 반영
 *      Summary 에 새 필드가 추가되어도 Detail 에 누락될 위험 구조적으로 제거.
 *
 *   3) 의미 관계의 정확성
 *      Detail 은 Summary 를 "포함"하는 관계이지 "한 종류"가 아님.
 *      Composition 이 이 관계를 정확히 표현.
 *
 *   4) Lombok @Builder 기본 조합과의 호환
 *      상속 방식은 @SuperBuilder 로의 전환이 필요하나
 *      Composition 은 @Builder 기본 동작 그대로 사용 가능.
 *
 *   5) "상속보다 합성" 원칙
 *      Effective Java Item 18 권고 준수.
 *
 * ────────────────────────────────────────────────────────────
 * 응답 JSON 구조 — 중첩 구조
 * ────────────────────────────────────────────────────────────
 * 본 DTO 의 직렬화 결과는 Summary 필드가 "summary" 키 아래 중첩되는 형태:
 *
 *   {
 *     "summary": {
 *       "propertyId": "...", "leaseType": "CHARTER", "aptNm": "...",
 *       "districtName": "...", "address": "...", "floor": 12,
 *       "excluUseAr": 59.84, "areaInPyeong": 18.10, "deposit": 45000,
 *       "monthlyRent": null, "buildYear": 2015, "dataSource": "USER",
 *       "status": "ACTIVE", "registeredAt": "...", "lastUpdated": "..."
 *     },
 *     "umdNm": "역삼동",
 *     "jibun": "123-45",
 *     "sggCd": "11680",
 *     "dealDate": "2026-04-20",
 *     "registeredUserId": "user_1234",
 *     "modifiedAt": null,
 *     "reviewCount": 12,
 *     "avgRating": 4.3
 *   }
 *
 * ────────────────────────────────────────────────────────────
 * 상태별 조회 허용 범위 (섹션 6.4)
 * ────────────────────────────────────────────────────────────
 * ACTIVE    — 조회 허용
 * COMPLETED — 조회 허용.
 *             추천 결과 조회 시점에는 ACTIVE 였으나 이후 거래완료로 변경된 경우,
 *             UI 가 "거래완료된 매물입니다" 안내를 표시하려면 매물 상세 정보 노출이
 *             전제되어야 함(F003-요구사항 5).
 * DELETED   — 조회 불가, 404(E4201) 반환.
 *
 * ────────────────────────────────────────────────────────────
 * 리뷰 통계 병합 (R-F004D-04)
 * ────────────────────────────────────────────────────────────
 * reviewCount, avgRating 은 매물 테이블이 아닌 REVIEW_STATISTICS 에서 조회.
 * 서비스 계층이 매물 조회 이후 별도 통계 병합을 수행하여 주입.
 *
 * ────────────────────────────────────────────────────────────
 * NULL 허용 필드 규약 (섹션 8.1.3)
 * ────────────────────────────────────────────────────────────
 * registeredUserId, modifiedAt  — 배치 매물 또는 미수정 매물에서 null
 * dealDate                      — 미등록 매물에서 null 가능
 * avgRating                     — 리뷰 0건일 때 0 (섹션 7.6.2 "리뷰 미존재 시 0")
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyDetailDto {

    // ============================================================
    // Summary 포함 (Composition)
    // ============================================================

    /**
     * 매물 요약 정보. PropertySummaryDto 의 모든 필드를 포함.
     * 목록 조회와 상세 조회가 공유하는 핵심 필드 집합.
     */
    private PropertySummaryDto summary;

    // ============================================================
    // Detail 전용 추가 필드 (섹션 7.6.2)
    // ============================================================

    /**
     * 법정동명.
     */
    private String umdNm;

    /**
     * 지번.
     */
    private String jibun;

    /**
     * 시군구 코드. 서울시 25개 자치구 코드.
     */
    private String sggCd;

    /**
     * 계약일자. 미등록 매물은 null 가능.
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dealDate;

    /**
     * 등록자 식별자. 배치 매물은 null.
     */
    private String registeredUserId;

    /**
     * 수정 시각. 미수정 매물은 null.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime modifiedAt;

    /**
     * 리뷰 개수. REVIEW_STATISTICS 테이블에서 주입.
     */
    private Integer reviewCount;

    /**
     * 평균 별점. 리뷰 미존재 시 0 (섹션 7.6.2).
     */
    private BigDecimal avgRating;
}
