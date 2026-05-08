package com.wherehouse.review.domain;

import java.time.LocalDateTime;

/**
 * 전세/월세 리뷰 엔티티 공통 인터페이스
 *
 * ReviewCharter, ReviewMonthly가 구현하여
 * 서비스 레이어에서 타입 무관하게 DTO 변환 가능하도록 한다.
 */
public interface ReviewBase {
    Long getReviewId();
    String getPropertyId();
    String getUserId();
    Integer getRating();
    String getContent();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
