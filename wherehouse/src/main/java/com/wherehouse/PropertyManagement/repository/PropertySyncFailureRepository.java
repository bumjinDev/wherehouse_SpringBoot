package com.wherehouse.PropertyManagement.repository;

import com.wherehouse.PropertyManagement.entity.PropertySyncFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Redis 동기화 실패 기록 Repository — F008 보상 아키텍처.
 *
 * 대응 테이블: PROPERTY_SYNC_FAILURES
 *
 * 사용 지점:
 *   - CharterPropertyWriteService.recordSyncFailure()
 *       : afterCommit 콜백에서 Redis 동기화 실패 시 실패 레코드 INSERT (save)
 *   - PropertySyncRetryScheduler.retryFailedSync()
 *       : 미해결 레코드 조회 (findByResolvedOrderByFailTimeAsc)
 *       : 재시도 결과 반영 — retryCount 증가 또는 resolved='Y' 전환 (save)
 *
 * 기본 제공 메서드 사용:
 *   - save()       : 실패 기록 INSERT + 재시도 결과 UPDATE
 *   - findById()   : 향후 개별 실패 건 조회 시 사용 가능
 */
@Repository
public interface PropertySyncFailureRepository extends JpaRepository<PropertySyncFailure, Long> {

    /**
     * 미해결 실패 레코드를 실패 시각 오름차순으로 조회.
     *
     * 스케줄러가 1분 주기로 호출하여, 먼저 실패한 건부터 순차 재시도한다.
     * 호출 예: findByResolvedOrderByFailTimeAsc("N")
     *
     * @param resolved 해결 여부. 'N' = 미해결, 'Y' = 해결 완료.
     * @return 미해결 실패 레코드 목록 (실패 시각 오름차순)
     */
    List<PropertySyncFailure> findByResolvedOrderByFailTimeAsc(String resolved);
}
