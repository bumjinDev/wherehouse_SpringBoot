package com.wherehouse.logger.result.R02;

import lombok.Builder;
import lombok.Data;

/**
 * R-02 서브: L1 캐시 조회 결과
 *
 * 1단계 캐시(dto:{centerGeohashId}) 조회 결과를 기록한다.
 *
 * 조회 대상: 사용자 요청 좌표의 중심 격자 ID로 생성된 캐시 키
 *
 * L1 히트 시: 전체 응답 DTO가 JSON으로 저장되어 있음
 * - 즉시 역직렬화하여 응답 반환 (R-03~R-07 스킵)
 * - 예상 시간: L1 조회(1-2ms) + JSON 역직렬화(20-100ms)
 *
 * L1 미스 시: L2 캐시 조회 단계로 진행
 */
@Data
@Builder
public class R02L1CacheResult {

    /**
     * L1 캐시 키
     *
     * 형식: "dto:{centerGeohashId}"
     *
     * 예시: "dto:wydm7p1"
     */
    private String cacheKey;

    /**
     * L1 캐시 히트 여부
     * - true: 캐시 히트 (전체 응답 DTO 반환)
     * - false: 캐시 미스 (L2 조회 진행)
     */
    private boolean hit;

    /**
     * 캐시된 JSON 데이터 크기 (bytes)
     *
     * L1 히트 시에만 값 존재
     *
     * 예시: 12,450 (약 12KB)
     */
    private Integer valueSize;

    /**
     * L1 캐시 조회 시간 (나노초)
     *
     * 측정 범위: redisSingleDataService.getSingleData(level1CacheKey) 호출 시간
     *
     * Redis 네트워크 RTT(Round Trip Time) 측정
     *
     * 예상 시간:
     * - 정상: 1-2ms (1,000,000 ~ 2,000,000 ns)
     * - 느림: 5ms 이상 (네트워크 지연)
     *
     * 용도: Redis 연결 상태 모니터링
     */
    private long l1CacheGetDurationNs;

    /**
     * L1 JSON 역직렬화 시간 (나노초)
     *
     * 측정 범위: objectMapper.readValue(...) 실행 시간
     *
     * L1 히트 시에만 측정됨
     *
     * 예상 시간:
     * - 작은 DTO: 20ms
     * - 큰 DTO (CCTV 100개+): 100ms
     *
     * 용도: CPU 병목 여부 확인
     * - 만약 L1 조회는 2ms인데 역직렬화가 100ms라면 CPU 병목
     */
    private Long l1JsonDeserializeDurationNs;
}