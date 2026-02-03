package com.wherehouse.logger.result.R03;

import lombok.Builder;
import lombok.Data;

/**
 * R-03 서브 루틴: L2 캐시 쓰기 결과 (격자별)
 *
 * DB에서 조회한 CCTV 데이터를 Redis L2 캐시에 저장하는
 * 단일 격자에 대한 캐시 쓰기 작업의 결과를 기록한다.
 *
 * 캐시 쓰기 패턴:
 * - Key: "data:{geohashId}:cctv" (예: "data:wydm7p1:cctv")
 * - Value: List<CctvGeo> JSON 직렬화
 * - TTL: 24시간
 *
 * 이 DTO는 격자별로 생성되므로,
 * 9개 격자를 조회했다면 최대 9개의 R03CacheWriteResult가 생성된다.
 */
@Data
@Builder
public class R03CacheWriteResult {

    /**
     * 캐시 대상 격자 ID
     *
     * 예시: "wydm7p1"
     */
    private String geohashId;

    /**
     * Redis 캐시 키
     *
     * 형식: "data:{geohashId}:cctv"
     * 예시: "data:wydm7p1:cctv"
     */
    private String cacheKey;

    /**
     * 캐싱된 CCTV 데이터 개수
     *
     * 예시: 23 (해당 격자에서 23개의 CCTV 데이터를 캐싱함)
     *
     * 주의: 0개인 경우에도 캐싱함 (빈 리스트 [])
     * 이유: 다음 요청에서 "데이터가 없다"는 정보도 캐시로 활용
     */
    private int dataCount;

    /**
     * 캐싱된 JSON 문자열 크기 (bytes)
     *
     * 예시: 5420 (약 5.4KB)
     *
     * 측정 방법: JSON 직렬화 후 문자열의 length()
     * 용도: Redis 메모리 사용량 분석
     */
    private int dataSize;

    /**
     * 캐시 쓰기 성공 여부
     * - true: Redis SET 명령 성공
     * - false: 예외 발생 (네트워크 오류, Redis 장애 등)
     */
    private boolean isSuccess;

    /**
     * 에러 메시지 (실패 시에만 값 존재)
     *
     * 예시: "Redis connection refused", "Serialization failed"
     */
    private String errorMessage;
}