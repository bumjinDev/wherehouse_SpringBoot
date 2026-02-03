package com.wherehouse.logger.result.R04;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * R-04 서브 루틴: 편의시설 API 결과
 *
 * 카카오맵 로컬 검색 API를 통해 15개 카테고리의 편의시설을 조회한 결과를 기록한다.
 *
 * 조회 카테고리 (15개):
 * SW8(지하철역), CS2(편의점), FD6(음식점), CE7(카페), MT1(대형마트),
 * BK9(은행), PO3(공공기관), CT1(문화시설), HP8(병원), PM9(약국),
 * PK6(주차장), OL7(주유소), SC4(학교), AC5(학원), AT4(관광명소)
 *
 * API 엔드포인트:
 * GET https://dapi.kakao.com/v2/local/search/category.json
 *
 * 캐싱 전략:
 * - 캐시 키: "amenity:{latitude}:{longitude}:{radius}"
 * - TTL: 24시간
 * - 캐시 히트 시 API 호출 생략
 */
@Data
@Builder
public class R04AmenityApiResult {

    /**
     * 캐시 히트 여부
     * - true: Redis 캐시에서 조회
     * - false: 카카오 API 호출 (15개 카테고리 모두 조회)
     */
    private boolean cached;

    /**
     * Redis 캐시 키
     *
     * 형식: "amenity:{latitude}:{longitude}:{radius}"
     * 예시: "amenity:37.5665:126.9780:500"
     */
    private String cacheKey;

    /**
     * 조회된 카테고리 개수
     *
     * 정상: 15 (모든 카테고리 조회)
     * 일부 실패 시: 15 미만
     */
    private int categoryCount;

    /**
     * 카테고리별 장소 개수
     *
     * Key: 카테고리 코드 (예: "CS2", "FD6")
     * Value: 해당 카테고리에서 조회된 장소 개수
     *
     * 예시:
     * {
     *   "CS2": 12,   // 편의점 12개
     *   "FD6": 45,   // 음식점 45개
     *   "CE7": 23    // 카페 23개
     * }
     *
     * 용도: 카테고리별 데이터 분포 분석
     */
    private Map<String, Integer> placesByCategory;

    /**
     * 전체 장소 개수 (모든 카테고리 합계)
     *
     * 예시: 234 (15개 카테고리에서 총 234개 장소 조회)
     */
    private int totalPlaces;

    /**
     * API 응답 JSON 크기 (bytes)
     *
     * 용도: Redis 메모리 사용량 분석
     */
    private Integer responseSize;

    /**
     * 편의시설 조회 실행 시간 (나노초)
     *
     * 측정 범위: try 블록 시작부터 종료까지
     * - 캐시 히트: Redis 조회 시간
     * - 캐시 미스: API 호출(15개 카테고리) + JSON 역직렬화 + Redis 저장 시간
     *
     * 측정 방법: System.nanoTime()
     *
     * 예상 시간: 약 300ms (가장 긴 작업)
     */
    private long executionTimeNs;

    /**
     * API 호출 성공 여부
     * - true: 정상 응답
     * - false: 예외 발생
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     */
    private String errorMessage;
}