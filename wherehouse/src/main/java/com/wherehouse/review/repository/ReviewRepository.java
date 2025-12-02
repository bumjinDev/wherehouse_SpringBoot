package com.wherehouse.review.repository;

import com.wherehouse.review.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 리뷰 Repository
 *
 * 설계 명세서: 7.1.2 A. REVIEWS (리뷰 원본)
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 중복 리뷰 체크 (애플리케이션 레벨 검증)
     *
     * 설계 명세서: 8.5.2 방어 메커니즘 - 1. 애플리케이션 레벨 검증
     *
     * @param propertyId 매물 ID
     * @param userId 사용자 ID
     * @return 리뷰 존재 여부
     */
    boolean existsByPropertyIdAndUserId(String propertyId, String userId);

    /**
     * 특정 매물에 대한 사용자의 리뷰 조회
     *
     * @param propertyId 매물 ID
     * @param userId 사용자 ID
     * @return 리뷰
     */
    Optional<Review> findByPropertyIdAndUserId(String propertyId, String userId);

    /**
     * 평균 별점 및 리뷰 개수 집계 (DTO 프로젝션)
     *
     * 설계 명세서: 8.4.2 통합 재산출 프로세스 - Step 1: 집계 쿼리 수행
     *
     * @param propertyId 매물 ID
     * @return [reviewCount, avgRating]
     */
    @Query("SELECT COUNT(r), COALESCE(AVG(r.rating), 0.0) " +
            "FROM Review r " +
            "WHERE r.propertyId = :propertyId")
    Object[] aggregateReviewStats(@Param("propertyId") String propertyId);
}