package com.wherehouse.logger.result.R02;

import lombok.Builder;
import lombok.Data;

/**
 * R-02 서브: L2 캐시 조회 결과 (격자별)
 *
 * 9개 격자 각각에 대한 2단계 캐시(data:{geohashId}:cctv) 조회 결과를 기록한다.
 *
 * L2 히트 시: 해당 격자의 CCTV 리스트가 JSON으로 저장되어 있음
 * L2 미스 시: R-03에서 DB 조회 필요
 *
 * B-03 병목:
 * - 9개 격자를 for 루프로 순회하며 각각 Redis 조회 (N+1 문제)
 * - 각 조회마다 네트워크 RTT 발생 (12ms × 9 = 108ms)
 */
@Data
@Builder
public class R02L2CacheResult {

    /**
     * 격자 ID
     *
     * 예시: "wydm7p1", "wydm7p2", ...
     *
     * 중심 격자 1개 + 인접 8개 = 총 9개
     */
    private String geohashId;

    /**
     * L2 캐시 키
     *
     * 형식: "data:{geohashId}:cctv"
     *
     * 예시: "data:wydm7p1:cctv"
     */
    private String cacheKey;

    /**
     * L2 캐시 히트 여부
     * - true: 캐시 히트 (CCTV 데이터 있음)
     * - false: 캐시 미스 (DB 조회 필요)
     */
    private boolean hit;

    /**
     * 데이터 타입
     *
     * 고정값: "cctv"
     *
     * (파출소는 Geohash 기반 조회 안 함)
     */
    private String dataType;

    /**
     * CCTV 개수
     *
     * L2 히트 시에만 값 존재
     *
     * 예시: 15 (해당 격자에 CCTV 15개)
     */
    private Integer dataCount;

    /**
     * 캐시된 JSON 데이터 크기 (bytes)
     *
     * L2 히트 시에만 값 존재
     *
     * 예시: 2,300 (약 2.3KB)
     */
    private Integer dataSize;

    /**
     * [선택적] 이 격자의 L2 캐시 조회 시간 (나노초)
     *
     * 측정 범위: redisSingleDataService.getSingleData(cctvCacheKey) 1회 호출 시간
     *
     * 예상 시간: 12ms (12,000,000 ns)
     *
     * 용도: 9개 격자별 조회 시간 분포 확인
     *
     * 참고: 전체 for 루프 시간은 R02CacheResult.l2CacheTotalDurationNs에 기록됨
     */
    private Long l2CacheGetDurationNs;
}