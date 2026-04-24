package com.wherehouse.PropertyManagement.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * F002 전세 매물 수정 요청 DTO.
 *
 * 엔드포인트: PATCH /api/v1/properties/charter/{propertyId}
 *
 * PATCH 시맨틱 — 모든 필드가 선택. 요청 본문에 포함된 필드만 갱신 대상.
 *
 * 전세 전용 가변 속성만 선언:
 *   deposit   — 전세금(만원)
 *   buildYear — 건축연도
 *   dealDate  — 계약일자
 *
 * monthlyRent 필드가 존재하지 않는다.
 * 기존 단일 DTO(PropertyUpdateRequestDto)에서 서비스 계층이 담당하던
 * "전세 매물에 monthlyRent 포함 시 E4001" 검증이 DTO 구조상 원천 불필요하다.
 *
 * 불변 속성(sggCd, jibun, aptNm, floor, excluUseAr)과
 * 시스템 관리 필드(propertyId, dataSource, status, registeredUserId, registeredAt, modifiedAt)는
 * 선언하지 않았다. Jackson 기본 동작이 알 수 없는 필드를 무시하므로 바인딩 단계에서
 * 에러가 발생하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharterUpdateRequestDto {

    /**
     * 전세금(만원). 선택.
     */
    @Positive(message = "전세금은 양수여야 합니다")
    private Integer deposit;

    /**
     * 건축연도. 선택. 1900 이상.
     */
    @Min(value = 1900, message = "건축연도는 1900 이상이어야 합니다")
    private Integer buildYear;

    /**
     * 계약일자. 선택. ISO 8601 날짜(YYYY-MM-DD).
     */
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "계약일자는 YYYY-MM-DD 형식이어야 합니다")
    private String dealDate;
}