package com.wherehouse.logger.result.R05;

import lombok.Builder;
import lombok.Data;

/**
 * R-05 서브 루틴: CCTV 필터링 결과
 *
 * 9개 격자에서 조회한 모든 CCTV 데이터를 통합하고,
 * 사용자가 요청한 반경 내의 CCTV만 필터링한 결과를 기록한다.
 *
 * 필터링 과정:
 * 1. 9개 격자에서 조회한 CCTV를 단일 리스트로 통합
 * 2. 각 CCTV와 사용자 위치 간 거리 계산 (Haversine 공식)
 * 3. 반경 내 CCTV만 필터링
 * 4. 필터링된 CCTV의 카메라 대수 합계 계산
 *
 * 필터링 필요성:
 * - 9-Block 그리드는 약 450m × 450m 영역을 포함
 * - 사용자는 정확한 반경(예: 500m) 내 데이터만 필요
 * - 따라서 반경 밖 데이터를 제거해야 함
 *
 * 병목 가능성:
 * - allCctvList가 수백 개일 경우 거리 계산이 누적되어 병목 발생 가능
 * - filterExecutionTimeNs로 전체 필터링 루프 시간 측정
 */
@Data
@Builder
public class R05CctvFilterResult {

    /**
     * 필터링 전 전체 CCTV 개수
     *
     * 9개 격자에서 조회한 모든 CCTV의 총 개수
     *
     * 예시: 147개 (9개 격자 전체)
     */
    private int totalCctvBeforeFilter;

    /**
     * 필터링 후 CCTV 개수
     *
     * 사용자가 요청한 반경 내에 있는 CCTV 개수
     *
     * 예시: 89개 (반경 500m 내)
     */
    private int totalCctvAfterFilter;

    /**
     * 필터링 후 총 카메라 대수
     *
     * 각 CCTV의 cameraCount를 합산한 값
     * (1개의 CCTV에 여러 대의 카메라가 설치될 수 있음)
     *
     * 예시: 178대 (89개 CCTV, 평균 2대/CCTV)
     */
    private int totalCameraCount;

    /**
     * 필터율 (0.0 ~ 1.0)
     *
     * 계산: totalCctvAfterFilter / totalCctvBeforeFilter
     *
     * 예시: 0.605 (60.5%의 CCTV가 반경 내에 포함됨)
     *
     * 의미: 필터율이 낮을수록 9-Block 밖의 불필요한 데이터가 많았음
     */
    private double filterRate;

    /**
     * CCTV 필터링 루프 전체 실행 시간 (나노초)
     *
     * 측정 범위: for 루프 시작부터 종료까지
     * - 각 CCTV에 대해 거리 계산 (calculateDistance)
     * - 반경 체크 및 리스트 추가
     * - 카메라 대수 누적
     *
     * 측정 방법: System.nanoTime()
     *
     * 병목 가능성:
     * - allCctvList가 200개 이상일 경우 수 밀리초 소요 가능
     * - 각 거리 계산이 ~1μs이지만 수백 번 누적되면 병목
     *
     * 예상 시간:
     * - 100개: ~0.1ms
     * - 500개: ~0.5ms
     * - 1000개: ~1ms
     */
    private long filterExecutionTimeNs;

    /**
     * CCTV 필터링 성공 여부
     * - true: 정상 실행
     * - false: 예외 발생 (거의 발생하지 않음)
     */
    private boolean isSuccess;
}