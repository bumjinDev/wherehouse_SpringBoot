// R-03: DB 조회 결과
package com.wherehouse.logger.result.R03;

import lombok.Data;
import lombok.Builder;

import java.util.List;

/**
 * R-03 단계 전체 결과를 담는 메인 DTO
 *
 * R-02에서 캐시 미스가 발생한 격자들에 대해 DB 조회를 수행하고,
 * 조회된 데이터를 다시 L2 캐시에 저장하는 전체 과정의 결과를 기록한다.
 *
 * 포함 정보:
 * - 입력: R-02에서 넘어온 캐시 미스 격자 목록
 * - CCTV DB 조회 결과 (쿼리 실행 정보)
 * - L2 캐시 쓰기 결과 (격자별 캐싱 성공 여부)
 * - 전체 실행 상태 (성공/실패, 에러 메시지)
 */
@Data
@Builder
public class R03DbResult {

    // ============================================
    // 입력 정보: R-02 결과에서 전달받은 데이터
    // ============================================

    /**
     * R-02에서 캐시 미스가 발생한 CCTV 격자 ID 목록
     * 이 격자들에 대해서만 DB 조회를 수행한다.
     *
     * 예시: ["wydm7p1", "wydm7p4", "wydm7nx"]
     */
    private List<String> inputCctvMissGrids;

    // ============================================
    // CCTV DB 조회 결과
    // ============================================

    /**
     * CCTV DB 쿼리 실행 결과
     * - 쿼리 대상 격자 ID 목록
     * - 조회된 총 행 수
     * - 격자별 행 수
     * - 쿼리 실행 시간 (나노초)
     */
    private R03CctvQueryResult cctvQueryResult;

    /**
     * CCTV 데이터를 L2 캐시에 쓴 결과 목록
     * DB에서 조회된 각 격자별 데이터를 Redis L2 캐시에 저장한 결과를 기록한다.
     *
     * 리스트 크기 = inputCctvMissGrids 크기 (격자별로 1개씩)
     */
    private List<R03CacheWriteResult> cctvCacheWrites;

    // ============================================
    // 실행 상태
    // ============================================

    /**
     * R-03 단계 전체 성공 여부
     * - true: DB 조회 및 캐시 쓰기 모두 성공
     * - false: 하나라도 실패
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     */
    private String errorMessage;
}