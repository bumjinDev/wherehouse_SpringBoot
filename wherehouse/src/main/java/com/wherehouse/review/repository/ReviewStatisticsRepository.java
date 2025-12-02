package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewStatistics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 리뷰 통계 Repository
 *
 * 설계 명세서: 7.1.2 B. REVIEW_STATISTICS (리뷰 통계)
 */
@Repository
public interface ReviewStatisticsRepository extends JpaRepository<ReviewStatistics, String> {

    /**
     * 매물 ID로 통계 조회
     *
     * @param propertyId 매물 ID (PK)
     * @return 리뷰 통계
     */
    // JpaRepository의 기본 메서드 findById(String propertyId) 사용
}