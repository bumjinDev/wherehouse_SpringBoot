package com.wherehouse.logger.result.R03;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * R-03 서브 루틴: CCTV DB 쿼리 실행 결과
 *
 * DB에서 CCTV 데이터를 조회하는 단일 쿼리의 실행 정보를 기록한다.
 *
 * 쿼리 패턴:
 * SELECT * FROM CCTV_GEO WHERE geohash_id IN (?, ?, ...)
 *
 * 성능 측정 항목:
 * - 쿼리 대상 격자 개수
 * - 조회된 총 행 수 (CCTV 개수)
 * - 격자별 행 수 분포
 * - 쿼리 실행 시간 (나노초 단위)
 */
@Data
@Builder
public class R03CctvQueryResult {

    /**
     * 쿼리 대상 격자 ID 목록
     * WHERE geohash_id IN (...) 절에 들어간 값들
     *
     * 예시: ["wydm7p1", "wydm7p4", "wydm7nx"]
     * 최대 9개 (R-02에서 모두 미스난 경우)
     */
    private List<String> queryGeohashIds;

    /**
     * 조회된 총 CCTV 행 수
     *
     * 예시: 147 (9개 격자에서 총 147개의 CCTV가 조회됨)
     */
    private int totalRowsReturned;

    /**
     * 격자별 조회된 행 수 분포
     * Key: geohashId (예: "wydm7p1")
     * Value: 해당 격자에서 조회된 CCTV 개수 (예: 23)
     *
     * 예시:
     * {
     *   "wydm7p1": 23,
     *   "wydm7p4": 18,
     *   "wydm7nx": 0
     * }
     *
     * 용도: 격자별 데이터 분포 분석, 불균형 감지
     */
    private Map<String, Integer> rowsPerGrid;

    /**
     * 쿼리 실행 시간 (나노초)
     *
     * JPA 쿼리 메서드 호출 시작부터 결과 리스트 반환까지의 시간
     * 측정 방법: System.nanoTime()
     *
     * 예시: 12500000 (약 12.5ms)
     */
    private long queryExecutionTimeNs;

    /**
     * 쿼리 실행 성공 여부
     * - true: 쿼리 정상 실행 (결과가 0건이어도 성공)
     * - false: 예외 발생
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     *
     * 예시: "Connection timeout", "Table not found"
     */
    private String errorMessage;
}