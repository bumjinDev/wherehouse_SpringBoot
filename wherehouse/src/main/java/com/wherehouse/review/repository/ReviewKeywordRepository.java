package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

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
     * CASE WHEN을 사용한 조건부 집계로 긍정(score > 0), 부정(score < 0) 키워드를 각각 카운팅
     *
     * @param propertyId 매물 ID
     * @return Object[0]: 긍정 키워드 수, Object[1]: 부정 키워드 수 (Long 타입, null 가능)
     */
    @Query("SELECT " +
            "SUM(CASE WHEN rk.score > 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN rk.score < 0 THEN 1 ELSE 0 END) " +
            "FROM ReviewKeyword rk " +
            "INNER JOIN Review r ON rk.reviewId = r.reviewId " +
            "WHERE r.propertyId = :propertyId")
    Object[] aggregateKeywordStats(@Param("propertyId") String propertyId);

    /**
     * 특정 리뷰의 모든 키워드 삭제
     *
     * 호출 위치: ReviewWriteService.updateReview() - Step 3
     * 사용 목적: 리뷰 수정 시 기존 키워드를 삭제하여 새로운 키워드로 교체
     *
     * Spring Data JPA의 메서드 네이밍 규칙에 따라 자동으로 DELETE 쿼리 생성
     * 실행 SQL: DELETE FROM REVIEW_KEYWORDS WHERE REVIEW_ID = ?
     *
     * @param reviewId 리뷰 ID
     */
    void deleteByReviewId(Long reviewId);

    /**
     * 특정 리뷰의 키워드 목록 조회
     *
     * @param reviewId 리뷰 ID
     * @return 키워드 엔티티 리스트
     */
    List<ReviewKeyword> findByReviewId(Long reviewId);
}