package com.wherehouse.PropertyManagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * F003 매물 상태 변경 요청 DTO (설계 명세서 섹션 7.4.1, 9.3).
 *
 * 매물 등록자가 본인 매물의 상태를 거래완료 또는 삭제로 전이시킬 때 사용.
 *
 * 1차 계층 유효성 검증 (섹션 9.3.2):
 *   - targetStatus 필수 존재 (@NotBlank)
 *   - targetStatus 값 범위: COMPLETED 또는 DELETED만 허용 (@Pattern)
 *     ACTIVE는 허용되지 않음 — 상태 전이가 아닌 현재 상태 유지 요청이므로
 *     이미 ACTIVE인 매물에 대한 ACTIVE 요청은 무의미, 이미 종료 상태인 매물에
 *     대한 ACTIVE 요청은 역방향 전이로 금지(섹션 6.2).
 *
 * 2차 계층 — 상태 전이 허용성 검증 (섹션 9.3.4):
 *   1차 검증이 "요청 값 자체의 형식 적합성"만 판단하므로 ACTIVE 이외의 값이면 통과한다.
 *   그러나 현재 상태와 targetStatus 조합이 섹션 6.2의 허용 전이 규칙에 맞는지는
 *   서비스 계층이 매물 조회 후 검증하며, 금지 전이 시 InvalidStatusTransitionException →
 *   E4002로 응답.
 *
 *   예를 들어 "COMPLETED 매물에 DELETED 전이 요청"은 1차 검증(@Pattern)은 통과하지만
 *   2차 검증에서 금지 전이로 판정되어 E4002 반환.
 *
 * 허용 전이 (섹션 6.2 재확인):
 *   ACTIVE → COMPLETED   (거래완료)
 *   ACTIVE → DELETED     (삭제)
 *   그 외 모든 전이 조합은 금지
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyStatusUpdateRequestDto {

    /**
     * 전이 목표 상태.
     * 허용 값: COMPLETED, DELETED (ACTIVE 불허, 섹션 7.4.1).
     */
    @NotBlank(message = "목표 상태는 필수입니다")
    @Pattern(regexp = "^(COMPLETED|DELETED)$",
            message = "목표 상태는 COMPLETED 또는 DELETED만 허용됩니다")
    private String targetStatus;
}