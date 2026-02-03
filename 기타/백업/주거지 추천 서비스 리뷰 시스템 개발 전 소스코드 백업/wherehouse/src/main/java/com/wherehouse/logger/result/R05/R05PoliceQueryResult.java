package com.wherehouse.logger.result.R05;

import lombok.Builder;
import lombok.Data;

/**
 * R-05 서브 루틴: 파출소 조회 결과
 *
 * 사용자 위치에서 가장 가까운 파출소를 찾기 위해 Native Query를 실행한 결과를 기록한다.
 *
 * 조회 방식:
 * - Repository: policeOfficeGeoRepository.findNearestPoliceStations()
 * - Native Query: ST_Distance_Sphere 함수 사용
 * - 반환: 가장 가까운 파출소 1개
 *
 * 병목 지점 (B-01):
 * 이 쿼리는 공간 인덱스를 활용하지 못하고 Full Table Scan을 수행하므로
 * 데이터가 많을 경우 성능 문제가 발생할 수 있다.
 *
 * 성능 측정 목적:
 * - Native Query 실행 시간 측정
 * - 병목 지점 개선 전후 비교 기준
 */
@Data
@Builder
public class R05PoliceQueryResult {

    /**
     * Native Query 실행 시간 (나노초)
     *
     * 측정 범위: findNearestPoliceStations() 호출 시작부터 반환까지
     *
     * 측정 방법: System.nanoTime()
     *
     * 예상 시간: 데이터 규모에 따라 다름
     * - 소규모: ~10ms
     * - 대규모: ~100ms 이상 (병목)
     */
    private long queryDurationNs;

    /**
     * 파출소 발견 여부
     * - true: 가장 가까운 파출소 발견
     * - false: 파출소를 찾지 못함
     */
    private boolean found;

    /**
     * 가장 가까운 파출소의 주소
     *
     * 예시: "서울특별시 중구 태평로1가 31"
     *
     * found=false일 경우: null
     */
    private String nearestAddress;

    /**
     * 가장 가까운 파출소까지의 거리 (미터)
     *
     * 예시: 523.7
     *
     * Haversine 공식으로 계산된 직선 거리
     *
     * found=false일 경우: Double.MAX_VALUE
     */
    private Double nearestDistance;

    /**
     * 파출소 조회 성공 여부
     * - true: 정상 실행 (파출소를 못 찾아도 성공)
     * - false: 예외 발생
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     */
    private String errorMessage;
}