package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewStatisticsCharter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewStatisticsCharterRepository extends JpaRepository<ReviewStatisticsCharter, String> {
}
