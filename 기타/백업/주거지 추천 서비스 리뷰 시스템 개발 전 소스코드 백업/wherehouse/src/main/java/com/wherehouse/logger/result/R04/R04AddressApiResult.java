package com.wherehouse.logger.result.R04;

import lombok.Builder;
import lombok.Data;

/**
 * R-04 서브 루틴: 주소 변환 API 결과
 *
 * 카카오맵 Reverse Geocoding API를 통해 좌표를 주소로 변환한 결과를 기록한다.
 *
 * API 엔드포인트:
 * GET https://dapi.kakao.com/v2/local/geo/coord2address.json
 *
 * 캐싱 전략:
 * - 캐시 키: "address:{latitude}:{longitude}"
 * - TTL: 24시간
 * - 캐시 히트 시 API 호출 생략
 */
@Data
@Builder
public class R04AddressApiResult {

    /**
     * 캐시 히트 여부
     * - true: Redis 캐시에서 조회
     * - false: 카카오 API 호출
     */
    private boolean cached;

    /**
     * Redis 캐시 키
     *
     * 형식: "address:{latitude}:{longitude}"
     * 예시: "address:37.5665:126.9780"
     */
    private String cacheKey;

    /**
     * 도로명 주소
     *
     * 예시: "서울특별시 중구 세종대로 110"
     */
    private String roadAddress;

    /**
     * 지번 주소
     *
     * 예시: "서울특별시 중구 태평로1가 31"
     */
    private String jibunAddress;

    /**
     * API 응답 JSON 크기 (bytes)
     *
     * 용도: Redis 메모리 사용량 분석
     */
    private Integer responseSize;

    /**
     * 주소 변환 실행 시간 (나노초)
     *
     * 측정 범위: try 블록 시작부터 종료까지
     * - 캐시 히트: Redis 조회 시간
     * - 캐시 미스: API 호출 + JSON 역직렬화 + Redis 저장 시간
     *
     * 측정 방법: System.nanoTime()
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