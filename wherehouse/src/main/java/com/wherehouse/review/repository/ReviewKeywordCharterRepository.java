package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewKeywordCharter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewKeywordCharterRepository extends JpaRepository<ReviewKeywordCharter, Long> {

    @Query("SELECT " +
            "SUM(CASE WHEN rk.score > 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN rk.score < 0 THEN 1 ELSE 0 END) " +
            "FROM ReviewKeywordCharter rk " +
            "INNER JOIN ReviewCharter r ON rk.reviewId = r.reviewId " +
            "WHERE r.propertyId = :propertyId")
    List<Object[]> aggregateKeywordStats(@Param("propertyId") String propertyId);

    void deleteByReviewId(Long reviewId);

    List<ReviewKeywordCharter> findByReviewId(Long reviewId);
}
