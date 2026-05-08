package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewCharter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ReviewCharterRepository extends JpaRepository<ReviewCharter, Long> {

    @Query("SELECT r FROM ReviewCharter r " +
            "WHERE (:propertyId IS NULL OR r.propertyId = :propertyId) " +
            "AND (:keyword IS NULL OR " +
            "     r.content LIKE %:keyword% OR " +
            "     EXISTS (SELECT k FROM ReviewKeywordCharter k WHERE k.reviewId = r.reviewId AND k.keyword = :keyword))")
    Page<ReviewCharter> findReviews(
            @Param("propertyId") String propertyId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value =
            "SELECT property_id FROM properties_charter WHERE apt_nm LIKE :name || '%'",
            nativeQuery = true)
    List<String> findPropertyIdsByName(@Param("name") String name);

    @Query("SELECT r FROM ReviewCharter r WHERE r.propertyId IN :propertyIds")
    Page<ReviewCharter> findByPropertyIdIn(@Param("propertyIds") List<String> propertyIds, Pageable pageable);

    @Query(value =
            "SELECT property_id, apt_nm FROM properties_charter " +
                    "WHERE property_id IN :propertyIds",
            nativeQuery = true)
    List<Object[]> findPropertyNames(@Param("propertyIds") Set<String> propertyIds);

    boolean existsByPropertyIdAndUserId(String propertyId, String userId);

    @Query("SELECT COUNT(r), COALESCE(AVG(r.rating), 0.0) " +
            "FROM ReviewCharter r " +
            "WHERE r.propertyId = :propertyId")
    List<Object[]> aggregateReviewStats(@Param("propertyId") String propertyId);

    @Query("SELECT r FROM ReviewCharter r " +
            "WHERE r.propertyId IN :propertyIds " +
            "AND (:keyword IS NULL OR r.content LIKE %:keyword%)")
    Page<ReviewCharter> findReviewsByPropertyIdsAndKeyword(
            @Param("propertyIds") List<String> propertyIds,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    Page<ReviewCharter> findAll(Pageable pageable);

    @Query(value =
            "SELECT property_id, apt_nm, '전세' as type FROM properties_charter " +
                    "WHERE apt_nm LIKE '%' || :keyword || '%'",
            nativeQuery = true)
    List<Object[]> searchPropertiesByName(@Param("keyword") String keyword);

    @Modifying
    @Query("DELETE FROM ReviewCharter r WHERE r.userId = :userId")
    long deleteAllByUserId(@Param("userId") String userId);
}
