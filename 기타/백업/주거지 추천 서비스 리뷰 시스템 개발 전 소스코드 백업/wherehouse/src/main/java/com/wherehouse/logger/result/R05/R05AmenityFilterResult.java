package com.wherehouse.logger.result.R05;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * R-05 서브 루틴: 편의시설 필터링 결과
 *
 * R-04에서 카카오맵 API로 조회한 편의시설 데이터를
 * 사용자가 요청한 반경 내로 필터링한 결과를 기록한다.
 *
 * 필터링 과정:
 * 1. R-04에서 받은 15개 카테고리의 편의시설 데이터 확인
 * 2. 각 장소별 사용자 위치와의 거리 계산 (Haversine 공식)
 * 3. 반경 내 장소만 필터링
 * 4. 거리 기준 오름차순 정렬
 * 5. 카테고리별 AmenityDetailDto 생성
 *
 * 필터링 필요성:
 * - 카카오맵 API는 반경을 파라미터로 받지만 정확하지 않을 수 있음
 * - 사용자가 요청한 정확한 반경(예: 500m) 내 데이터만 제공해야 함
 *
 * 병목 가능성:
 * - 편의시설이 수백~수천 개일 경우 거리 계산이 누적되어 병목 발생 가능
 * - filterExecutionTimeNs로 전체 필터링 루프 시간 측정
 */
@Data
@Builder
public class R05AmenityFilterResult {

    /**
     * 필터링 전 카테고리별 장소 개수
     *
     * Key: 카테고리 코드 (예: "CS2", "FD6")
     * Value: 해당 카테고리에서 API가 반환한 장소 개수
     *
     * 예시:
     * {
     *   "CS2": 25,   // 편의점 25개 (필터 전)
     *   "FD6": 80,   // 음식점 80개 (필터 전)
     *   "CE7": 45    // 카페 45개 (필터 전)
     * }
     *
     * 용도: 필터 전후 비교, 카테고리별 필터율 분석
     */
    private Map<String, Integer> placesBeforeFilter;

    /**
     * 필터링 후 카테고리별 장소 개수
     *
     * Key: 카테고리 코드 (예: "CS2", "FD6")
     * Value: 반경 내 필터링된 장소 개수
     *
     * 예시:
     * {
     *   "CS2": 12,   // 편의점 12개 (필터 후)
     *   "FD6": 45,   // 음식점 45개 (필터 후)
     *   "CE7": 23    // 카페 23개 (필터 후)
     * }
     */
    private Map<String, Integer> placesAfterFilter;

    /**
     * 필터링 전 전체 장소 개수
     *
     * 모든 카테고리의 장소 수 합계
     *
     * 예시: 420개 (15개 카테고리 전체)
     */
    private int totalBeforeFilter;

    /**
     * 필터링 후 전체 장소 개수
     *
     * 반경 내 모든 카테고리의 장소 수 합계
     *
     * 예시: 234개 (반경 500m 내)
     */
    private int totalAfterFilter;

    /**
     * 필터율 (0.0 ~ 1.0)
     *
     * 계산: totalAfterFilter / totalBeforeFilter
     *
     * 예시: 0.557 (55.7%의 장소가 반경 내에 포함됨)
     *
     * 의미: 필터율이 낮을수록 API가 반경 밖 데이터를 많이 반환했음
     */
    private double filterRate;

    /**
     * 편의시설 필터링 루프 전체 실행 시간 (나노초)
     *
     * 측정 범위: 편의시설 처리 시작부터 종료까지
     * - 15개 카테고리 순회
     * - 각 장소별 거리 계산 (calculateDistance)
     * - 반경 체크 및 PlaceDto 생성
     * - 거리 기준 정렬
     * - AmenityDetailDto 생성
     *
     * 측정 방법: System.nanoTime()
     *
     * 병목 가능성:
     * - 편의시설이 300개 이상일 경우 수 밀리초 소요 가능
     * - 특히 음식점(FD6) 카테고리는 수십~수백 개 반환 가능
     * - 각 거리 계산이 ~1μs이지만 수백 번 누적 + 정렬 시간 포함
     *
     * 예상 시간:
     * - 200개: ~0.5ms
     * - 500개: ~1-2ms
     * - 1000개: ~3-5ms
     */
    private long filterExecutionTimeNs;

    /**
     * 편의시설 필터링 성공 여부
     * - true: 정상 실행
     * - false: 예외 발생 (거의 발생하지 않음)
     */
    private boolean isSuccess;
}