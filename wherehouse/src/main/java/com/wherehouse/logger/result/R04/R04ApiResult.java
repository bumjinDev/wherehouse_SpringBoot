// R-04: 외부 API 호출 결과
package com.wherehouse.logger.result.R04;

import lombok.Data;
import lombok.Builder;

/**
 * R-04 단계 전체 결과를 담는 메인 DTO
 *
 * *** 순차 실행으로 변경됨 ***
 * 성능 측정을 위해 비동기 병렬 처리를 제거하고 순차 실행으로 변경하였다.
 * 각 작업의 순수 실행 시간을 정확히 측정하여 병목 지점을 식별한 후,
 * 추후 비동기 처리로 최적화할 예정이다.
 *
 * 순차 처리 작업:
 * 1. 주소 변환 API (카카오맵 Reverse Geocoding)
 * 2. 편의시설 검색 API (카카오맵 로컬 검색 - 15개 카테고리)
 * 3. 검거율 조회 (내부 DB - 주소 변환 완료 후 실행)
 *
 * 성능 비교:
 * - 순차 실행: 주소(100ms) + 편의시설(300ms) + 검거율(20ms) = 420ms
 * - 병렬 실행 예상: max(100ms, 300ms, 20ms) = 300ms
 */
@Data
@Builder
public class R04ApiResult {

    // ============================================
    // 입력 정보
    // ============================================

    /**
     * 요청 좌표 - 위도
     */
    private double latitude;

    /**
     * 요청 좌표 - 경도
     */
    private double longitude;

    /**
     * 검색 반경 (미터)
     *
     * 편의시설 검색에 사용
     */
    private int radius;

    // ============================================
    // 순차 작업 전체 실행 정보
    // ============================================

    /**
     * 전체 순차 작업 개수
     *
     * 고정값: 3 (주소, 편의시설, 검거율)
     */
    private int totalSequentialTasks;

    /**
     * R-04 전체 실행 시간 (나노초)
     *
     * 의미: 3개 작업의 합계 시간
     * 계산: addressApiResult.executionTimeNs
     *      + amenityApiResult.executionTimeNs
     *      + arrestRateResult.executionTimeNs
     *
     * 용도: 비동기 전환 시 성능 개선율 계산 기준
     */
    private long totalExecutionTimeNs;

    // ============================================
    // 개별 API 결과 참조
    // ============================================

    /**
     * 주소 변환 API 결과
     *
     * 도로명/지번 주소, 캐시 여부, 응답 크기, 실행 시간 등 포함
     */
    private R04AddressApiResult addressApiResult;

    /**
     * 검거율 조회 결과
     *
     * 추출된 '구' 이름, 검거율, 데이터 존재 여부, 실행 시간 등 포함
     */
    private R04ArrestRateResult arrestRateResult;

    /**
     * 편의시설 검색 API 결과
     *
     * 카테고리별 장소 개수, 전체 장소 수, 캐시 여부, 실행 시간 등 포함
     */
    private R04AmenityApiResult amenityApiResult;

    // ============================================
    // 실행 상태
    // ============================================

    /**
     * R-04 단계 전체 성공 여부
     * - true: 모든 작업 정상 완료 (일부 API 실패해도 전체는 성공)
     * - false: 치명적 오류 발생
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     */
    private String errorMessage;
}