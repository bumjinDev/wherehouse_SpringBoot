package com.wherehouse.review.enums;

/**
 * 리뷰 통계 갱신 연산 유형
 *
 * ============================================================================
 * [설계 근거]
 * ============================================================================
 * 메서드 오버로딩 대신 Enum을 선택한 이유:
 *   1. 명시적 의도 표현: 호출부에서 StatisticsOperation.CREATE 등으로 의도 명확화
 *   2. 단일 진입점: 로깅/측정 로직을 중앙 집중화하여 일관된 성능 측정 가능
 *   3. 확장성: 향후 BULK_CREATE, BULK_DELETE 등 추가 시 Enum만 확장
 *
 * [연산별 산술 공식]
 * ============================================================================
 * CREATE: count_new = count + 1,  sum_new = sum + newRating
 * UPDATE: count_new = count,      sum_new = sum - oldRating + newRating
 * DELETE: count_new = count - 1,  sum_new = sum - deletedRating
 * ============================================================================
 */
public enum StatisticsOperation {

    /**
     * 리뷰 신규 작성
     * - count 증가 (+1)
     * - sum에 newRating 추가
     */
    CREATE,

    /**
     * 리뷰 수정
     * - count 불변
     * - sum에서 oldRating 차감 후 newRating 추가
     */
    UPDATE,

    /**
     * 리뷰 삭제
     * - count 감소 (-1)
     * - sum에서 deletedRating 차감
     */
    DELETE
}
