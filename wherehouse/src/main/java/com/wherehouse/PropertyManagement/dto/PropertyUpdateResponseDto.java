package com.wherehouse.PropertyManagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * F002 매물 수정 응답 DTO (설계 명세서 섹션 7.3.2).
 *
 * HTTP 200 OK 와 함께 반환되는 응답 본문.
 * 수정 완료된 매물의 식별자와 실제 변경된 필드 목록을 제공한다.
 *
 * changedFields 의 의미:
 *   요청 본문에 포함된 필드 중 실제로 기존 값과 다른 값으로 갱신된 필드만 나열.
 *   예를 들어 요청에 deposit=47000이 포함되었으나 기존 값도 47000이면 응답에서 제외.
 *   클라이언트가 "어떤 필드가 실제로 바뀌었는지" 확인할 수 있게 하는 용도.
 *
 *   이 정책은 섹션 7.3.2에 명시된 "실제로 변경된 필드명 목록" 표현의 구체화이며,
 *   구현 세부(동일 값 갱신 요청을 포함시킬지 제외할지)는 서비스 계층의 판단 사항이다.
 *
 * Redis 동기화 선택적 갱신과의 연결 (섹션 9.2.5):
 *   changedFields 에 포함된 속성에 대응하는 Redis 인덱스만 선택 갱신된다.
 *   예: deposit 만 변경 시 가격 Sorted Set 의 Score 만 갱신, 평수 인덱스는 무변경.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyUpdateResponseDto {

    /**
     * 수정된 매물 식별자.
     */
    private String propertyId;

    /**
     * 수정 시각. ISO 8601(yyyy-MM-ddTHH:mm:ss).
     * RDB MODIFIED_AT 컬럼의 갱신 값과 일치.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime modifiedAt;

    /**
     * 실제로 변경된 필드명 목록.
     * 예: ["deposit"] 또는 ["monthlyRent", "dealDate"]
     */
    private List<String> changedFields;
}