package com.wherehouse.recommand.batch.event;

import com.wherehouse.recommand.batch.dto.Property;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치 데이터 수집 완료 이벤트
 * 
 * BatchScheduler가 국토교통부 API로부터 매물 데이터 수집 및 ID 생성을 완료한 후 발행하는 이벤트입니다.
 * RdbSyncListener가 이 이벤트를 구독하여 RDB 적재 및 Redis 동기화 작업을 비동기로 수행합니다.
 * 
 * 이벤트 기반 아키텍처 적용 목적:
 * - 데이터 수집(BatchScheduler)과 데이터 적재(RdbSyncListener)의 관심사 분리
 * - RDB 적재 작업의 비동기 처리를 통한 배치 스케줄러 부하 감소
 * - 향후 다중 리스너 확장 가능성 확보
 * 
 * @author 정범진
 * @since 2025-12-05
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataCollectionCompletedEvent {

    /**
     * 수집된 전세 매물 목록
     * 
     * MD5 Hash 기반 propertyId가 부여된 상태의 매물 객체들입니다.
     * leaseType 필드가 "전세"인 매물들이 포함됩니다.
     */
    private List<Property> charterProperties;

    /**
     * 수집된 월세 매물 목록
     * 
     * MD5 Hash 기반 propertyId가 부여된 상태의 매물 객체들입니다.
     * leaseType 필드가 "월세"인 매물들이 포함됩니다.
     */
    private List<Property> monthlyProperties;

    /**
     * 데이터 수집 완료 시각
     * 
     * 배치 스케줄러가 모든 지역구의 데이터 수집 및 ID 생성을 완료한 시각입니다.
     * 이벤트 발행 시점의 타임스탬프로, 추적 및 로깅 목적으로 사용됩니다.
     */
    private LocalDateTime collectedAt;

    /**
     * 전체 매물 수 (전세 + 월세)
     * 
     * 배치 실행 결과 요약 정보로, 로깅 및 모니터링에 활용됩니다.
     */
    private Integer totalCount;

    /**
     * 전세 매물 수 조회
     */
    public int getCharterCount() {
        return charterProperties != null ? charterProperties.size() : 0;
    }

    /**
     * 월세 매물 수 조회
     */
    public int getMonthlyCount() {
        return monthlyProperties != null ? monthlyProperties.size() : 0;
    }

    /**
     * 이벤트 발행 전 데이터 검증
     * 
     * @return 유효한 이벤트인 경우 true
     */
    public boolean isValid() {
        return (charterProperties != null && !charterProperties.isEmpty()) ||
               (monthlyProperties != null && !monthlyProperties.isEmpty());
    }
}
