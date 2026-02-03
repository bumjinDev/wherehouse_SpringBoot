package com.wherehouse.logger.result.R04;

import lombok.Builder;
import lombok.Data;

/**
 * R-04 서브 루틴: 검거율 조회 결과
 *
 * 주소에서 추출한 '구' 단위를 기준으로 내부 DB(ArrestRate 테이블)에서
 * 해당 지역의 검거율 데이터를 조회한 결과를 기록한다.
 *
 * 처리 흐름:
 * 1. 주소 변환 API 결과 확인 (순차 실행이므로 이미 완료됨)
 * 2. 도로명 주소에서 '구' 추출 (extractGu 메서드)
 * 3. Redis 캐시 조회 시도
 * 4. 캐시 미스 시 ArrestRate 테이블에서 해당 '구'의 검거율 조회
 * 5. 조회 성공 시 Redis 캐시에 저장
 *
 * 캐싱 전략:
 * - 캐시 키: "arrest_rate:{구이름}" (예: "arrest_rate:중구")
 * - TTL: 24시간
 * - 검거율 데이터는 거의 변하지 않으므로 캐싱 효과가 높음
 *
 * 주의사항:
 * - 주소 변환이 먼저 완료되어야 실행 가능 (순차 실행으로 보장됨)
 */
@Data
@Builder
public class R04ArrestRateResult {

    /**
     * 캐시 히트 여부
     * - true: Redis 캐시에서 조회
     * - false: DB 조회
     */
    private boolean cached;

    /**
     * Redis 캐시 키
     *
     * 형식: "arrest_rate:{구이름}"
     * 예시: "arrest_rate:중구", "arrest_rate:강남구"
     */
    private String cacheKey;

    /**
     * 추출된 구 이름
     *
     * 예시: "중구", "강남구"
     *
     * extractGu 메서드로 주소 문자열에서 추출
     * (예: "서울특별시 중구 세종대로 110" → "중구")
     */
    private String guName;

    /**
     * 조회된 검거율 (0.0 ~ 1.0)
     *
     * 예시: 0.7523 (75.23%)
     *
     * 데이터 없거나 조회 실패 시: 0.0
     */
    private Double arrestRate;

    /**
     * DB에서 데이터를 찾았는지 여부
     * - true: ArrestRate 테이블에 해당 '구' 데이터 존재
     * - false: 데이터 없음 (arrestRate는 0.0으로 설정)
     *
     * 주의: 캐시 히트 시에도 true (원본 데이터 존재했으므로)
     */
    private boolean dataFound;

    /**
     * 검거율 조회 실행 시간 (나노초)
     *
     * 측정 범위: try 블록 시작부터 종료까지
     * - 캐시 히트: 주소 확인 + extractGu + Redis 조회 시간 (약 1-2ms)
     * - 캐시 미스: 주소 확인 + extractGu + Redis 조회 + DB 조회 + Redis 저장 시간 (약 20ms)
     *
     * 측정 방법: System.nanoTime()
     */
    private long executionTimeNs;

    /**
     * 검거율 조회 성공 여부
     * - true: 정상 실행 (데이터가 없어도 성공)
     * - false: 예외 발생
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     */
    private String errorMessage;
}