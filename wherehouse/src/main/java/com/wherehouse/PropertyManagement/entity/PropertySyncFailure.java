package com.wherehouse.PropertyManagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Redis 동기화 실패 기록 Entity — F008 보상 아키텍처.
 *
 * 대응 테이블: PROPERTY_SYNC_FAILURES
 *
 * RDB 커밋 확정 후 Redis 동기화가 실패하면, 실패 정보를 이 테이블에 기록한다.
 * 복구 스케줄러(PropertySyncRetryScheduler)가 주기적으로 미해결 레코드를 조회하여
 * Redis 재동기화를 시도하고, 재시도 상한 초과 시 RDB 원복(DELETE)을 수행한다.
 *
 * 설계 근거:
 *   - F008 설계판단 논의기록 섹션 5.4~5.7: A+B 혼합 보상 전략
 *   - F008 1차 구현 설계안 섹션 4: DDL 및 컬럼 설계
 *
 * 1차 구현 범위에서 각 컬럼에 들어가는 값:
 *   LEASE_TYPE      — 'CHARTER' 고정 (전세 매물 생성만 대상)
 *   OPERATION_TYPE  — 'CREATE' 고정 (매물 생성만 대상)
 *   FAIL_STEP       — 'FULL' 고정 (Redis 명령 전체를 하나의 단위로 취급)
 *   RESOLVED_METHOD — 'RETRY' (스케줄러 복구 성공) 또는 'ROLLBACK' (상한 초과 원복)
 *
 * 향후 확장:
 *   LEASE_TYPE      — 'MONTHLY' 추가
 *   OPERATION_TYPE  — 'UPDATE', 'STATUS_CHANGE' 추가
 *   FAIL_STEP       — 'HASH', 'ZSET_PRICE', 'ZSET_AREA', 'BOUNDS_PRICE', 'BOUNDS_AREA' 세분화
 */
@Entity
@Table(name = "PROPERTY_SYNC_FAILURES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySyncFailure {

    /**
     * 실패 기록 식별자. Oracle IDENTITY 자동 생성.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "FAILURE_ID")
    private Long failureId;

    /**
     * 동기화 실패 대상 매물 식별자. MD5 해시 32자.
     */
    @Column(name = "PROPERTY_ID", length = 32, nullable = false)
    private String propertyId;

    /**
     * 임대 유형. 1차: 'CHARTER' 고정.
     */
    @Column(name = "LEASE_TYPE", length = 10, nullable = false)
    private String leaseType;

    /**
     * 실패 발생 시점의 작업 유형.
     * 1차: 'CREATE' 고정. 향후 'UPDATE', 'STATUS_CHANGE' 확장.
     * 스케줄러가 재시도 시 어떤 sync 메서드를 호출할지 분기하는 키.
     */
    @Column(name = "OPERATION_TYPE", length = 20, nullable = false)
    private String operationType;

    /**
     * 실패 발생 지점.
     * 1차: 'FULL' 고정 (Redis 명령 전체를 하나의 단위로 취급).
     * 2차: 개별 Redis 명령 단위로 세분화 예정.
     */
    @Column(name = "FAIL_STEP", length = 50, nullable = false)
    private String failStep;

    /**
     * 실패 원인 메시지. 예외 메시지를 500자 이내로 잘라서 저장.
     */
    @Column(name = "FAIL_REASON", length = 500)
    private String failReason;

    /**
     * 최초 실패 발생 시각. DDL DEFAULT SYSTIMESTAMP.
     */
    @Column(name = "FAIL_TIME")
    private LocalDateTime failTime;

    /**
     * 스케줄러 재시도 횟수. DDL DEFAULT 0.
     */
    @Column(name = "RETRY_COUNT")
    private Integer retryCount;

    /**
     * 재시도 상한. DDL DEFAULT 5.
     * 이 값을 초과하면 스케줄러가 RDB 원복(DELETE)을 수행.
     */
    @Column(name = "MAX_RETRIES")
    private Integer maxRetries;

    /**
     * 해결 여부. 'N' = 미해결, 'Y' = 해결 완료. DDL DEFAULT 'N'.
     */
    @Column(name = "RESOLVED", length = 1)
    private String resolved;

    /**
     * 해결 완료 시각. resolved='Y'로 전환된 시점.
     */
    @Column(name = "RESOLVED_TIME")
    private LocalDateTime resolvedTime;

    /**
     * 해결 방법.
     * 'RETRY'    — 스케줄러 재시도 성공으로 Redis 동기화 복구.
     * 'ROLLBACK' — 재시도 상한 초과 또는 매물 부재로 RDB+Redis 원복.
     */
    @Column(name = "RESOLVED_METHOD", length = 20)
    private String resolvedMethod;
}
