package com.wherehouse.PropertyManagement.scheduler;

import com.wherehouse.PropertyManagement.entity.PropertySyncFailure;
import com.wherehouse.PropertyManagement.repository.PropertyCharterRegistrationRepository;
import com.wherehouse.PropertyManagement.repository.PropertySyncFailureRepository;
import com.wherehouse.PropertyManagement.service.CharterPropertyWriteService;
import com.wherehouse.PropertyManagement.execption.customExceptions.PropertyNotFoundException;
import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Redis 동기화 실패 복구 스케줄러 — F008 보상 아키텍처.
 *
 * 1분 주기로 PROPERTY_SYNC_FAILURES 테이블의 미해결 레코드를 조회하여
 * Redis 재동기화를 시도한다. 재시도 상한 초과 시 RDB 원복(DELETE)을 수행하여
 * 불일치 상태가 무한히 지속되는 것을 방지한다.
 *
 * 설계 근거:
 *   - F008 설계판단 논의기록 섹션 5.4~5.7: A+B 혼합 보상 전략
 *   - F008 1차 구현 설계안 섹션 9: 스케줄러 로직
 *
 * 1차 구현 범위:
 *   - CHARTER + CREATE만 처리 (LEASE_TYPE='CHARTER', OPERATION_TYPE='CREATE')
 *   - FAIL_STEP='FULL' 고정 (sync 전체를 하나의 단위로 재실행)
 *
 * 분기 구조:
 *   분기 1 (retryCount >= maxRetries): 상한 초과 → Oracle DELETE + Redis 잔존 정리 → ROLLBACK
 *   분기 2 (retryCount < maxRetries):  재시도 → 성공 시 RETRY / 실패 시 retryCount 증가
 *   예외 케이스: 재시도 중 매물 부재(PropertyNotFoundException) → Redis 잔존 정리 → ROLLBACK
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PropertySyncRetryScheduler {

    private final PropertySyncFailureRepository failureRepository;
    private final PropertyCharterRegistrationRepository charterRepository;
    private final CharterPropertyWriteService charterWriteService;
    private final RedisHandler redisHandler;

    /**
     * 1분 주기 실행. 미해결 실패 레코드를 실패 시각 오름차순으로 순차 처리.
     *
     * fixedDelay = 60000: 이전 실행이 완료된 후 60초 뒤에 다음 실행.
     * 실행 중 예외가 발생해도 스케줄러 자체는 중단되지 않는다(Spring 기본 동작).
     */
    @Scheduled(fixedDelay = 60000)
    public void retryFailedSync() {

        List<PropertySyncFailure> failures =
                failureRepository.findByResolvedOrderByFailTimeAsc("N");

        if (failures.isEmpty()) {
            return;
        }

        log.info("[SYNC_SCHEDULER] 미해결 실패 {}건 처리 시작", failures.size());

        for (PropertySyncFailure failure : failures) {

            // ────────────────────────────
            // 분기 1: 상한 초과 → RDB + Redis 원복
            // ────────────────────────────
            if (failure.getRetryCount() >= failure.getMaxRetries()) {

                try {
                    // Oracle DELETE
                    charterRepository.deleteById(failure.getPropertyId());

                    // Redis 잔존 데이터 정리 (부분 실패로 Hash가 남아있을 수 있음)
                    cleanupRedisResidual(failure.getPropertyId());

                    failure.setResolved("Y");
                    failure.setResolvedMethod("ROLLBACK");
                    failure.setResolvedTime(LocalDateTime.now());
                    failureRepository.save(failure);

                    log.error("[SYNC_ROLLBACK] 재시도 상한 초과, RDB+Redis 원복 완료: propertyId={}",
                            failure.getPropertyId());

                } catch (Exception e) {
                    // 원복 자체가 실패 — 로그만 남기고 다음 주기에 재시도
                    log.error("[SYNC_ROLLBACK_FAILED] 원복 실패: propertyId={}, error={}",
                            failure.getPropertyId(), e.getMessage());
                }
                continue;
            }

            // ────────────────────────────
            // 분기 2: 재시도
            // ────────────────────────────
            try {
                charterWriteService.retrySyncForCreate(failure.getPropertyId());

                // 재시도 성공
                failure.setResolved("Y");
                failure.setResolvedMethod("RETRY");
                failure.setResolvedTime(LocalDateTime.now());
                failureRepository.save(failure);

                log.info("[SYNC_RETRY_OK] 재시도 성공 ({}회차): propertyId={}",
                        failure.getRetryCount() + 1, failure.getPropertyId());

            } catch (PropertyNotFoundException e) {
                // 매물이 다른 경로(F003 등)로 이미 삭제됨
                cleanupRedisResidual(failure.getPropertyId());

                failure.setResolved("Y");
                failure.setResolvedMethod("ROLLBACK");
                failure.setResolvedTime(LocalDateTime.now());
                failureRepository.save(failure);

                log.warn("[SYNC_ENTITY_GONE] 매물 부재, Redis 잔존 정리 후 종료: propertyId={}",
                        failure.getPropertyId());

            } catch (Exception e) {
                // 재시도 실패 — retryCount 증가
                failure.setRetryCount(failure.getRetryCount() + 1);
                failureRepository.save(failure);

                log.warn("[SYNC_RETRY_FAIL] 재시도 실패 ({}회): propertyId={}, error={}",
                        failure.getRetryCount(), failure.getPropertyId(), e.getMessage());
            }
        }
    }

    /**
     * Redis 잔존 데이터 정리.
     *
     * 부분 실패(시나리오2)로 Hash만 남아있거나, ZSet까지 남아있을 수 있다.
     * 존재 여부와 무관하게 삭제를 시도한다 — Redis의 ZREM, DEL은
     * 대상이 없어도 에러 없이 0을 반환하므로 안전하다.
     *
     * bounds는 정리하지 않는다:
     *   bounds는 "경계 확장" 전용이며, 매물 하나 제거로 축소하는 것은
     *   F009(정규화 기준 변경 감지) 영역이다.
     *   BoundsUpdater Javadoc: "경계 확장만 처리, 축소 없음. F009 승격 조건."
     *
     * @param propertyId 정리 대상 매물 식별자
     */
    private void cleanupRedisResidual(String propertyId) {

        try {
            String hashKey = "property:charter:" + propertyId;

            // Hash에서 districtName 조회 — 부분 실패로 Hash가 남아있을 수 있음
            Object districtNameRaw =
                    redisHandler.redisTemplate.opsForHash().get(hashKey, "districtName");

            if (districtNameRaw != null) {
                String districtName = districtNameRaw.toString();

                // ZSet에서 member 제거 — 존재하지 않아도 에러 없이 0 반환
                redisHandler.redisTemplate.opsForZSet().remove(
                        "idx:charterPrice:" + districtName, propertyId);
                redisHandler.redisTemplate.opsForZSet().remove(
                        "idx:area:" + districtName + ":전세", propertyId);
            }

            // Hash 삭제 — 존재하지 않아도 에러 없이 false 반환
            redisHandler.redisTemplate.delete(hashKey);

            log.info("[REDIS_CLEANUP] Redis 잔존 데이터 정리 완료: propertyId={}", propertyId);

        } catch (Exception e) {
            // Redis 정리 자체가 실패 — 로그만 남김
            log.error("[REDIS_CLEANUP_FAILED] Redis 잔존 정리 실패: propertyId={}, error={}",
                    propertyId, e.getMessage());
        }
    }
}