package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewStatisticsMonthly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewStatisticsMonthlyRepository extends JpaRepository<ReviewStatisticsMonthly, String> {
}
