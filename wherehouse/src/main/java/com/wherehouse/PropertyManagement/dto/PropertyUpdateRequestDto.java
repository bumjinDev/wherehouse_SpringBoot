package com.wherehouse.PropertyManagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * F002 매물 수정 요청 DTO (설계 명세서 섹션 7.3.1, 9.2).
 *
 * PATCH 시맨틱 — 요청 본문에 변경 필드만 포함되며, 미포함 필드는 변경하지 않음.
 * 모든 필드가 선택(Optional)이므로 @NotNull·@NotBlank 미적용.
 *
 * 수정 가능 필드 (섹션 7.3.1): deposit, monthlyRent, buildYear, dealDate
 *
 * 수정 불가 필드 — 요청 본문에 포함 시 E4001 (섹션 9.2.2 1차 계층 검증):
 *   1) 불변 속성 5개: sggCd, jibun, aptNm, floor, excluUseAr
 *      → 매물 식별자(MD5 해시) 생성의 기반이므로 수정 시 식별자 단일성이 깨짐(섹션 9.1.1)
 *   2) 시스템 관리 필드: propertyId, dataSource, status, registeredUserId,
 *                     registeredAt, modifiedAt
 *
 * 구현 주의 — 불변 속성 수정 시도 차단:
 *   본 DTO에는 불변 속성·시스템 관리 필드를 아예 선언하지 않았다. Jackson의 기본 동작은
 *   알 수 없는 JSON 필드를 무시하므로, 요청 본문에 예를 들어 "sggCd"가 포함되어도 DTO
 *   바인딩 단계에서는 에러가 발생하지 않는다.
 *
 *   이 동작은 "불변 속성 수정 시도 시 E4001" 요구사항(섹션 9.2.2)과 충돌한다.
 *   충돌 해결을 위해 두 접근 중 하나가 필요하다:
 *     (A) 컨트롤러/서비스 진입 시점에 원본 요청 JSON의 키 집합을 검사하여 금지 필드 포함 여부 판정
 *     (B) ObjectMapper 설정에 FAIL_ON_UNKNOWN_PROPERTIES=true 적용
 *
 *   (B) 방식은 전역 Jackson 설정을 변경하므로 다른 도메인(리뷰 등)에 파급 효과가 있다.
 *   (A) 방식을 권장하며, 구체 구현은 서비스 계층(PropertyWriteService.updateProperty) 진입부에서
 *   HttpServletRequest 또는 Map<String,Object> 파라미터 추가로 처리한다.
 *
 *   본 DTO 차원에서는 "값 범위 검증"만 담당하며, "금지 필드 포함 여부 검증"은 서비스 계층 책임.
 *
 * 임대 유형별 조건부 검증 (섹션 7.3.1):
 *   monthlyRent는 월세 매물에만 유효. 전세 매물 수정 요청에 포함 시 E4001.
 *   이 판정은 매물 조회 후 임대 유형을 확인해야 하므로 2차 계층(서비스)에서 수행.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyUpdateRequestDto {

    /**
     * 전세금 또는 월세 보증금(만원). 선택.
     */
    @Positive(message = "보증금은 양수여야 합니다")
    private Integer deposit;

    /**
     * 월세금(만원). 선택. 월세 매물에만 유효.
     * 전세 매물 수정 요청에 포함 시 서비스 계층에서 E4001로 거부.
     */
    @Positive(message = "월세금은 양수여야 합니다")
    private Integer monthlyRent;

    /**
     * 건축연도. 선택. 1900 이상.
     * 상한(현재 연도)은 서비스 계층에서 확정 검증.
     */
    @Min(value = 1900, message = "건축연도는 1900 이상이어야 합니다")
    private Integer buildYear;

    /**
     * 계약일자. 선택. ISO 8601 날짜(YYYY-MM-DD) 문자열.
     * TD-001: DB 컬럼이 VARCHAR2(10 BYTE)로 저장되어 있어 String 타입 유지 (향후 DATE 타입 전환 예정).
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "계약일자는 YYYY-MM-DD 형식이어야 합니다")
    private String dealDate;
}