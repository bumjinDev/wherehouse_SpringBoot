package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 리뷰 원본 엔티티
 *
 * 용도: 사용자 작성 리뷰 원본 저장
 * Redis 매핑: 없음 (RDB 직접 조회)
 *
 * 설계 명세서: 7.1.2 A. REVIEWS (리뷰 원본)
 */
@Entity
@Table(name = "REVIEWS",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_REVIEWS_PROPERTY_USER",
                        columnNames = {"PROPERTY_ID", "USER_ID"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_review_id")
    @SequenceGenerator(name = "seq_review_id", sequenceName = "SEQ_REVIEW_ID", allocationSize = 1)
    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    /**
     * 매물 ID (전세/월세 구분 없이 참조)
     * FK 제약조건 없음 - 전세/월세 테이블 모두 참조 가능
     */
    @Column(name = "PROPERTY_ID", length = 32, nullable = false)
    private String propertyId;

    @Column(name = "USER_ID", length = 50, nullable = false)
    private String userId;

    @Column(name = "RATING", nullable = false)
    private Integer rating;

    @Lob
    @Column(name = "CONTENT", nullable = false)
    private String content;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Builder
    public Review(String propertyId, String userId, Integer rating, String content) {
        this.propertyId = propertyId;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 리뷰 수정
     */
    public void update(Integer rating, String content) {
        this.rating = rating;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}