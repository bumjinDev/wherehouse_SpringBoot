package com.wherehouse.PropertyManagement.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * F001 월세 매물 등록 요청 DTO.
 *
 * 엔드포인트: POST /api/v1/properties/monthly
 *
 * URL 경로가 임대 유형(월세)을 확정하므로 leaseType 필드가 존재하지 않는다.
 * 전세 DTO(CharterCreateRequestDto)와의 차이점:
 *   1. monthlyRent 필드가 @NotNull 필수로 선언됨
 *   2. deposit 의 비즈니스 의미가 "월세 보증금"(전세 DTO에서는 "전세금")
 *
 * 기존 단일 DTO(PropertyCreateRequestDto)에서 monthlyRent 가 조건부 필수
 * (월세일 때만 필수, 전세에 포함 시 E4001)였던 검증이 DTO 분리로 해소된다.
 * 월세 DTO 에서 monthlyRent 는 무조건 필수이고, 전세 DTO 에는 필드 자체가 없다.
 *
 * 1차 계층 유효성 검증(설계 섹션 9.0, 9.1.3):
 *   Bean Validation 어노테이션으로 수행. 실패 시 E4001.
 *
 * 2차 계층 검증(서비스 계층):
 *   서울시 25개 자치구 코드 화이트리스트 검증은 서비스 계층에서 수행.
 *   buildYear 상한(현재 연도)은 DTO 단계에서 동적 판단이 어려워 서비스 계층에서 확정.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyCreateRequestDto {

    // ============================================================
    // 불변 속성 (MD5 해시 식별자 생성 입력, 설계 섹션 9.1.1)
    // ============================================================

    /**
     * 아파트·건물명. 1~100자. 불변 속성.
     */
    @NotBlank(message = "아파트명은 필수입니다")
    @Size(max = 100, message = "아파트명은 100자를 초과할 수 없습니다")
    private String aptNm;

    /**
     * 시군구 코드. 5자리 숫자. 불변 속성.
     * 서울시 25개 자치구 코드 화이트리스트 검증은 서비스 계층에서 수행.
     */
    @NotBlank(message = "시군구 코드는 필수입니다")
    @Pattern(regexp = "^\\d{5}$", message = "시군구 코드는 5자리 숫자여야 합니다")
    private String sggCd;

    /**
     * 법정동명. 1~100자. 불변 속성.
     */
    @NotBlank(message = "법정동명은 필수입니다")
    @Size(max = 100, message = "법정동명은 100자를 초과할 수 없습니다")
    private String umdNm;

    /**
     * 지번. 1~50자. 불변 속성.
     */
    @NotBlank(message = "지번은 필수입니다")
    @Size(max = 50, message = "지번은 50자를 초과할 수 없습니다")
    private String jibun;

    /**
     * 층수. -10 이상 100 이하(음수는 지하층). 불변 속성.
     */
    @NotNull(message = "층수는 필수입니다")
    @Min(value = -10, message = "층수는 -10 이상이어야 합니다")
    @Max(value = 100, message = "층수는 100 이하여야 합니다")
    private Integer floor;

    /**
     * 전용면적(㎡). 양수, 소수점 4자리까지. 불변 속성.
     */
    @NotNull(message = "전용면적은 필수입니다")
    @DecimalMin(value = "0.0001", inclusive = true, message = "전용면적은 양수여야 합니다")
    @Digits(integer = 10, fraction = 4, message = "전용면적은 소수점 4자리까지 허용됩니다")
    private BigDecimal excluUseAr;

    // ============================================================
    // 가변 속성
    // ============================================================

    /**
     * 월세 보증금(만원). 필수.
     */
    @NotNull(message = "보증금은 필수입니다")
    @Positive(message = "보증금은 양수여야 합니다")
    private Integer deposit;

    /**
     * 월세금(만원). 필수.
     * 전세 DTO(CharterCreateRequestDto)에는 이 필드가 존재하지 않는다.
     */
    @NotNull(message = "월세금은 필수입니다")
    @Positive(message = "월세금은 양수여야 합니다")
    private Integer monthlyRent;

    /**
     * 건축연도. 선택. 1900 이상.
     * 상한(현재 연도)은 서비스 계층에서 확정 검증.
     */
    @Min(value = 1900, message = "건축연도는 1900 이상이어야 합니다")
    private Integer buildYear;

    /**
     * 계약일자. 선택. ISO 8601 날짜(YYYY-MM-DD).
     * TD-001: DB 컬럼이 VARCHAR2(10 BYTE)로 저장되어 있어 String 타입 유지.
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "계약일자는 YYYY-MM-DD 형식이어야 합니다")
    private String dealDate;
}