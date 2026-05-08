package com.wherehouse.review.repository;

import com.wherehouse.review.domain.ReviewMonthly;
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
public interface ReviewMonthlyRepository extends JpaRepository<ReviewMonthly, Long> {

    @Query("SELECT r FROM ReviewMonthly r " +
            "WHERE (:propertyId IS NULL OR r.propertyId = :propertyId) " +
            "AND (:keyword IS NULL OR " +
            "     r.content LIKE %:keyword% OR " +
            "     EXISTS (SELECT k FROM ReviewKeywordMonthly k WHERE k.reviewId = r.reviewId AND k.keyword = :keyword))")
    Page<ReviewMonthly> findReviews(
            @Param("propertyId") String propertyId,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query(value =
            "SELECT r.* FROM REVIEWS_MONTHLY r " +
                    "INNER JOIN PROPERTIES_MONTHLY p ON r.PROPERTY_ID = p.PROPERTY_ID " +
                    "WHERE p.APT_NM LIKE :name || '%'",
            countQuery =
            "SELECT COUNT(*) FROM REVIEWS_MONTHLY r " +
                    "INNER JOIN PROPERTIES_MONTHLY p ON r.PROPERTY_ID = p.PROPERTY_ID " +
                    "WHERE p.APT_NM LIKE :name || '%'",
            nativeQuery = true)
    Page<ReviewMonthly> findByPropertyName(@Param("name") String name, Pageable pageable);

    @Query(value =
            "SELECT property_id, apt_nm FROM properties_monthly " +
                    "WHERE property_id IN :propertyIds",
            nativeQuery = true)
    List<Object[]> findPropertyNames(@Param("propertyIds") Set<String> propertyIds);

    boolean existsByPropertyIdAndUserId(String propertyId, String userId);

    @Query("SELECT COUNT(r), COALESCE(AVG(r.rating), 0.0) " +
            "FROM ReviewMonthly r " +
            "WHERE r.propertyId = :propertyId")
    List<Object[]> aggregateReviewStats(@Param("propertyId") String propertyId);

    @Query("SELECT r FROM ReviewMonthly r " +
            "WHERE r.propertyId IN :propertyIds " +
            "AND (:keyword IS NULL OR r.content LIKE %:keyword%)")
    Page<ReviewMonthly> findReviewsByPropertyIdsAndKeyword(
            @Param("propertyIds") List<String> propertyIds,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    Page<ReviewMonthly> findAll(Pageable pageable);

    @Query(value =
            "SELECT property_id, apt_nm, '월세' as type FROM properties_monthly " +
                    "WHERE apt_nm LIKE '%' || :keyword || '%'",
            nativeQuery = true)
    List<Object[]> searchPropertiesByName(@Param("keyword") String keyword);

    @Modifying
    @Query("DELETE FROM ReviewMonthly r WHERE r.userId = :userId")
    long deleteAllByUserId(@Param("userId") String userId);
}
