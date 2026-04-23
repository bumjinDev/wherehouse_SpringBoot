package com.wherehouse.PropertyManagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * F001 매물 등록 요청 DTO (설계 명세서 섹션 7.2.1).
 *
 * 1차 계층 유효성 검증(섹션 9.0, 9.1.3)을 Bean Validation 어노테이션으로 수행.
 * 실패 시 MethodArgumentNotValidException → GlobalExceptionHandlerProperty 에서 E4001로 매핑.
 *
 * 검증 항목 분류 (섹션 9.1.3):
 *   - 필수 필드 존재 확인
 *   - 값 범위 (floor, excluUseAr, buildYear, deposit, monthlyRent)
 *   - Enum 값 범위 (leaseType: CHARTER/MONTHLY)
 *   - 패턴 일치 (dealDate: ISO 8601)
 *   - 임대 유형별 조건부 필수성 (monthlyRent는 월세일 때만 필수)
 *   - 지역 코드 허용 값 (sggCd: 서울시 25개 자치구 코드)
 *
 * 주의 — 2차 계층 검증은 서비스 계층에서 수행 (섹션 9.0):
 *   1) 임대 유형별 조건부 필수성(전세 요청에 monthlyRent 포함 시 E4001)은 DTO 어노테이션으로
 *      표현하기 어렵고, 서비스 계층에서 leaseType·monthlyRent 조합을 판단.
 *   2) 서울시 25개 자치구 코드 화이트리스트는 @Pattern 만으로는 부적절하여 서비스 계층이 검증.
 *
 * Enum 표현 방식 (섹션 8.3):
 *   leaseType은 String으로 받고 서비스 계층에서 Enum으로 변환.
 *   기존 리뷰 도메인 DTO 관례와 일치.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyCreateRequestDto {

    // ============================================================
    // 공통 필드 (섹션 7.2.1 Request Body — 공통 필드)
    // ============================================================

    /**
     * 임대 유형. CHARTER(전세) 또는 MONTHLY(월세).
     * Enum 값 범위 검증은 @Pattern으로 1차 수행.
     */
    @NotBlank(message = "임대 유형은 필수입니다")
    @Pattern(regexp = "^(CHARTER|MONTHLY)$", message = "임대 유형은 CHARTER 또는 MONTHLY만 허용됩니다")
    private String leaseType;

    /**
     * 아파트·건물명. 1~100자.
     */
    @NotBlank(message = "아파트명은 필수입니다")
    @Size(max = 100, message = "아파트명은 100자를 초과할 수 없습니다")
    private String aptNm;

    /**
     * 시군구 코드. 서울시 25개 자치구 코드 중 하나.
     * 구체 화이트리스트 검증은 서비스 계층에서 수행.
     */
    @NotBlank(message = "시군구 코드는 필수입니다")
    @Pattern(regexp = "^\\d{5}$", message = "시군구 코드는 5자리 숫자여야 합니다")
    private String sggCd;

    /**
     * 법정동명. 1~100자.
     */
    @NotBlank(message = "법정동명은 필수입니다")
    @Size(max = 100, message = "법정동명은 100자를 초과할 수 없습니다")
    private String umdNm;

    /**
     * 지번. 1~50자.
     */
    @NotBlank(message = "지번은 필수입니다")
    @Size(max = 50, message = "지번은 50자를 초과할 수 없습니다")
    private String jibun;

    /**
     * 층수. 음수는 지하층(-10 이상 100 이하).
     */
    @NotNull(message = "층수는 필수입니다")
    @Min(value = -10, message = "층수는 -10 이상이어야 합니다")
    @Max(value = 100, message = "층수는 100 이하여야 합니다")
    private Integer floor;

    /**
     * 전용면적(㎡). 양수, 소수점 4자리까지.
     */
    @NotNull(message = "전용면적은 필수입니다")
    @DecimalMin(value = "0.0001", inclusive = true, message = "전용면적은 양수여야 합니다")
    @Digits(integer = 10, fraction = 4, message = "전용면적은 소수점 4자리까지 허용됩니다")
    private BigDecimal excluUseAr;

    /**
     * 건축연도. 선택 입력(섹션 7.2.1).
     * 값 범위: 1900 ~ 현재 연도.
     * 상한(현재 연도)은 DTO 단계에서 동적 판단이 어려워 서비스 계층에서 확정 검증.
     */
    @Min(value = 1900, message = "건축연도는 1900 이상이어야 합니다")
    private Integer buildYear;

    /**
     * 계약일자. 선택 입력, ISO 8601 날짜(YYYY-MM-DD) 문자열.
     * TD-001: DB 컬럼이 VARCHAR2(10 BYTE)로 저장되어 있어 String 타입 유지 (향후 DATE 타입 전환 예정).
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "계약일자는 YYYY-MM-DD 형식이어야 합니다")
    private String dealDate;

    // ============================================================
    // 임대 유형별 필드 (섹션 7.2.1 Request Body — 임대 유형별 필드)
    // ============================================================

    /**
     * 전세금 또는 월세 보증금(만원). 전세·월세 모두 필수.
     */
    @NotNull(message = "보증금은 필수입니다")
    @Positive(message = "보증금은 양수여야 합니다")
    private Integer deposit;

    /**
     * 월세금(만원). 월세일 때만 필수.
     * 전세 요청 시 포함되면 E4001 (서비스 계층에서 검증).
     */
    @Positive(message = "월세금은 양수여야 합니다")
    private Integer monthlyRent;
}
