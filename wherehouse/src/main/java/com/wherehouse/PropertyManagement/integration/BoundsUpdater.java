package com.wherehouse.PropertyManagement.integration;

import com.wherehouse.redis.handler.RedisHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

/**
 * bounds Hash 의 min/max 경계 확장 로직 공통 헬퍼.
 *
 * 설계 명세서: 섹션 8.2 (Redis 데이터 구조), 작업범위재설정 컨텍스트 4.2.
 * 주거지추천서비스 설계 7.2.3: "제로 분산 방지: min == max 인 경우 max = min + 1 강제".
 * RdbSyncListener.calculateBoundsForGroupFixed 도 동일한 제로 분산 방지 보정 수행.
 *
 * 본 구현 한계: 경계 확장만 처리. 매물 제거·가격 축소로 인한 재산출 없음.
 * F009 승격 조건 관찰 시 전체 재산출 전략 도입.
 *
 * 동시성: HGET → 비교 → HSET 시퀀스가 원자적이지 않음. 동시 등록 시 뒤에 커밋된
 * 작은 값이 앞에 커밋된 더 작은 값을 덮어쓸 수 있음. F008 후속 설계 승격 대상.
 */
@Component
@RequiredArgsConstructor
public class BoundsUpdater {

    private final RedisHandler redisHandler;

    /**
     * bounds Hash 의 두 경계 필드(minField, maxField)를 value 로 확장 시도.
     *
     * 기존 bounds 가 없으면 최초 생성.
     *   min = value, max = value + zeroRangeDelta 로 저장하여 분모 0 방지.
     *   이는 RdbSyncListener 가 배치 적재 직후 수행하는 보정(전세 price=+1000.0,
     *   area=+5.0, 월세 monthlyRent=+10.0)과 동일 의도이며 사용자 매물 등록 시에도
     *   동일 규약을 적용하여 추천 정규화 공식이 안정적으로 동작하도록 한다.
     * 있으면 value 가 현재 min 보다 작거나 max 보다 클 때만 해당 필드 갱신.
     *
     * @param boundsKey      bounds:{district}:{leaseType}
     * @param minField       갱신 대상 최솟값 필드명 (예: minPrice, minDeposit, minArea)
     * @param maxField       갱신 대상 최댓값 필드명
     * @param value          비교할 값 (deposit·monthlyRent·areaInPyeong)
     * @param zeroRangeDelta 최초 생성 시 max 에 더할 delta. 도메인별로 호출 측이 결정.
     * @return 경계 변경 발생 여부 (ScoreRecalc 트리거 판단용)
     */
    public boolean tryExtend(String boundsKey, String minField, String maxField,
                             double value, double zeroRangeDelta) {

        HashOperations<String, Object, Object> ops = redisHandler.redisTemplate.opsForHash();
        Object minRaw = ops.get(boundsKey, minField);
        Object maxRaw = ops.get(boundsKey, maxField);

        if (minRaw == null || maxRaw == null) {
            // 최초 생성
            ops.put(boundsKey, minField, String.valueOf(value));
            ops.put(boundsKey, maxField, String.valueOf(value + zeroRangeDelta));
            return true;
        }

        boolean changed = false;
        double currentMin = Double.parseDouble(minRaw.toString());
        double currentMax = Double.parseDouble(maxRaw.toString());

        if (value < currentMin) {
            ops.put(boundsKey, minField, String.valueOf(value));
            changed = true;
        }
        if (value > currentMax) {
            ops.put(boundsKey, maxField, String.valueOf(value));
            changed = true;
        }
        return changed;
    }
}