package com.wherehouse.VisitReservation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 등록자·탐색자 연락 정보 공통 DTO (설계 명세서 섹션 7.4, 7.9, 7.11).
 *
 * 예약 확정 시점에 한 쌍의 당사자에게만 공개되는 연락 경로 표현. 본 DTO 는 응답 객체의
 * registrant / searcher 양쪽에서 동일한 구조로 사용된다.
 *
 * 노출 시점:
 *   - 예약 STATUS=CONFIRMED 또는 COMPLETED 일 때만 응답에 포함
 *   - 예약 이전 (조회 단계) 에는 노출되지 않음
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContactInfoDto {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("username")
    private String username;

    /** 회원 프로필에 등록된 연락 경로 (예: 전화번호). 형식은 회원 프로필 시스템 스키마를 따른다. */
    @JsonProperty("contact")
    private String contact;
}
