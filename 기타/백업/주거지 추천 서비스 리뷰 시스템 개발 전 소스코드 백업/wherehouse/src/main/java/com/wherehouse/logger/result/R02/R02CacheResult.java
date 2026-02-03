// R-02: 캐시 조회 결과
package com.wherehouse.logger.result.R02;

import lombok.Data;
import lombok.Builder;

import java.util.List;

/**
 * R-02 단계 전체 캐시 조회 결과를 담는 메인 DTO
 *
 * 1단계(L1) 캐시와 2단계(L2) 캐시 조회 결과를 모두 포함한다.
 *
 * 처리 흐름:
 * 1. L1 캐시 조회 (1회)
 *    - Key: "dto:{centerGeohashId}"
 *    - 히트 시: 전체 응답 즉시 반환 (2-100ms)
 *    - 미스 시: L2 조회 진행
 *
 * 2. L2 캐시 조회 (9회, B-03 병목)
 *    - Key: "data:{geohashId}:cctv" × 9
 *    - for 루프로 순회 조회 (N+1 문제)
 *    - 예상 시간: 110ms (12ms × 9)
 *
 * 3. JSON 역직렬화 (CPU 연산)
 *    - L1 또는 L2 히트 시 발생
 *    - 예상 시간: 20-100ms
 *
 * 설계 목적:
 * - B-03 병목(L2 N+1 조회) 정확한 측정
 * - L1/L2 히트율 추적
 * - 캐시 효율성 분석
 */
@Data
@Builder
public class R02CacheResult {

    // ============================================
    // 기본 정보
    // ============================================

    /**
     * 1차 캐싱 확인 대상인 사용자 요청 좌표의 중심 격자 ID
     *
     * 예시: "wydm7p1"
     *
     * 이 값으로 L1 캐시 키 생성: "dto:wydm7p1"
     */
    private String centerGeohashId;

    /**
     * 2차 캐싱 확인 대상인 9개 격자 ID 목록
     *
     * 예시: ["wydm7p1", "wydm7p2", ..., "wydm7p9"]
     *
     * 중심 1개 + 인접 8개 = 총 9개
     */
    private List<String> nineBlockGeohashes;

    // ============================================
    // L1 캐시 결과
    // ============================================

    /**
     * 1차 캐시 히트 여부
     * - true: L1 히트 (전체 응답 반환, R-03~R-07 스킵)
     * - false: L1 미스 (L2 조회 진행)
     */
    private boolean l1CacheHit;

    /**
     * 1차 캐시 조회 결과
     *
     * L1 히트 시: 조회 시간, 데이터 크기, 역직렬화 시간 포함
     * L1 미스 시: null
     */
    private R02L1CacheResult l1CacheResult;

    // ============================================
    // L2 캐시 결과
    // ============================================

    /**
     * 2차 캐시 조회 실행 여부
     * - true: L1 미스로 L2 조회 실행됨
     * - false: L1 히트로 L2 조회 안 함
     */
    private boolean l2CacheRequired;

    /**
     * 2차 캐시 9개 격자별 조회 결과
     *
     * 각 격자별 히트/미스, 데이터 크기, 조회 시간 포함
     *
     * 크기: 최대 9개 (일부만 히트될 수 있음)
     */
    private List<R02L2CacheResult> l2CacheResults;

    /**
     * L2 캐시 히트 개수
     *
     * 예시: 6 (9개 중 6개 히트)
     */
    private int l2TotalHits;

    /**
     * L2 캐시 미스 개수
     *
     * 예시: 3 (9개 중 3개 미스)
     *
     * 미스된 격자는 R-03에서 DB 조회 필요
     */
    private int l2TotalMisses;

    // ============================================
    // [핵심] 시간 측정 필드
    // ============================================

    /**
     * L2 캐시 전체 조회 시간 (나노초)
     *
     * 측정 범위: L2 for 루프 전체 (9개 격자 순회 조회)
     *
     * 코드 위치:
     * ```java
     * long l2StartNs = System.nanoTime();
     * for (String geohashId : nineBlockGeohashes) {
     *     redisSingleDataService.getSingleData(cctvCacheKey);
     * }
     * long l2EndNs = System.nanoTime();
     * ```
     *
     * 예상 시간:
     * - B-03 병목: 110ms (12ms × 9회 + 오버헤드)
     * - 최적화 후: 20ms (MGET 사용 시)
     *
     * 용도:
     * - **B-03 병목의 핵심 지표**
     * - N+1 문제 개선 전후 비교
     * - Redis 네트워크 지연 측정
     *
     * 이 필드가 Gemini가 지적한 가장 중요한 측정 항목입니다!
     */
    private Long l2CacheTotalDurationNs;

    /**
     * L2 JSON 역직렬화 총 시간 (나노초)
     *
     * 측정 범위: L2 히트된 모든 격자의 objectMapper.readValue() 합계
     *
     * 예상 시간:
     * - 6개 격자 히트: 30-60ms
     *
     * 용도: CPU 병목 여부 확인
     */
    private Long l2JsonDeserializeTotalDurationNs;

    // ============================================
    // 실행 상태
    // ============================================

    /**
     * R-02 단계 전체 성공 여부
     * - true: L1 또는 L2 중 하나라도 히트
     * - false: 예외 발생
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     */
    private String errorMessage;
}