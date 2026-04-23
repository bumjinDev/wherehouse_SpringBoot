package com.wherehouse.PropertyManagement.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F003 매물 상태 변경 응답 DTO (설계 명세서 섹션 7.4.2).
 *
 * HTTP 200 OK 와 함께 반환되는 응답 본문.
 * 전이 이전·이후 상태를 모두 노출하여 클라이언트가 변경 내역을 명확히 확인할 수 있게 한다.
 *
 * previousStatus 의 용도:
 *   본 API에서 previousStatus는 항상 ACTIVE 가 된다(섹션 6.2 상태 전이 규칙상 ACTIVE에서만
 *   출발 가능). 그럼에도 필드를 명시적으로 응답에 포함시키는 근거는:
 *     1) UI가 "ACTIVE → COMPLETED 로 변경되었습니다" 형태로 안내 메시지를 구성할 때 활용
 *     2) 감사 로그·알림 등 후속 처리 컴포넌트가 전이 내역을 참조할 때 정보 손실 방지
 *     3) 향후 상태 전이 규칙이 확장될 경우(예: 관리자 권한으로 COMPLETED → ACTIVE 복원)
 *        응답 DTO 스키마 변경 없이 호환성 유지
 *
 * Redis 동기화 상태별 분기와의 연결 (섹션 9.3.5):
 *   currentStatus 값에 따라 Redis 동기화 동작이 달라진다:
 *     COMPLETED: Sorted Set Member 제거 + 매물 Hash 의 status 필드만 갱신
 *     DELETED:   Sorted Set Member 제거 + 매물 Hash 전면 제거
 *   이 분기는 IndexSyncService (F008) 내부에서 처리되며 본 응답 DTO는 결과만 반영.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyStatusUpdateResponseDto {

    /**
     * 상태 변경 대상 매물 식별자.
     */
    private String propertyId;

    /**
     * 전이 이전 상태. 본 API에서는 항상 ACTIVE.
     */
    private String previousStatus;

    /**
     * 전이 이후 상태. COMPLETED 또는 DELETED.
     */
    private String currentStatus;

    /**
     * 상태 변경 시각. ISO 8601(yyyy-MM-ddTHH:mm:ss).
     * RDB MODIFIED_AT 컬럼의 갱신 값과 일치.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime modifiedAt;
}