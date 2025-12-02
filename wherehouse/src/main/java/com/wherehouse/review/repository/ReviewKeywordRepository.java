package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 리뷰 키워드 Repository
 *
 * 설계 명세서: 7.1.2 C. REVIEW_KEYWORDS (리뷰 키워드)
 */
@Repository
public interface ReviewKeywordRepository extends JpaRepository<ReviewKeyword, Long> {

    /**
     * 특정 매물의 긍정/부정 키워드 개수 집계
     *
     * 설계 명세서: 8.4.3 키워드 통계 재산출 로직
     *
     * @param propertyId 매물 ID
     * @return [positiveCount, negativeCount]
     */
    @Query("SELECT " +
            "SUM(CASE WHEN rk.score > 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN rk.score < 0 THEN 1 ELSE 0 END) " +
            "FROM ReviewKeyword rk " +
            "INNER JOIN Review r ON rk.reviewId = r.reviewId " +
            "WHERE r.propertyId = :propertyId")
    Object[] aggregateKeywordStats(@Param("propertyId") String propertyId);
}