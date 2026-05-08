package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewKeywordMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewKeywordMonthlyRepository extends JpaRepository<ReviewKeywordMonthly, Long> {

    @Query("SELECT " +
            "SUM(CASE WHEN rk.score > 0 THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN rk.score < 0 THEN 1 ELSE 0 END) " +
            "FROM ReviewKeywordMonthly rk " +
            "INNER JOIN ReviewMonthly r ON rk.reviewId = r.reviewId " +
            "WHERE r.propertyId = :propertyId")
    List<Object[]> aggregateKeywordStats(@Param("propertyId") String propertyId);

    void deleteByReviewId(Long reviewId);

    List<ReviewKeywordMonthly> findByReviewId(Long reviewId);
}
