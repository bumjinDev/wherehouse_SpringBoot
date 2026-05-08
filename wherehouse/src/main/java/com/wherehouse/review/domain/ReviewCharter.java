package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "REVIEWS_CHARTER",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_REVIEWS_CHARTER_PROP_USER",
                        columnNames = {"PROPERTY_ID", "USER_ID"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewCharter {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_review_charter_id")
    @SequenceGenerator(name = "seq_review_charter_id", sequenceName = "SEQ_REVIEW_CHARTER_ID", allocationSize = 1)
    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

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
    public ReviewCharter(String propertyId, String userId, Integer rating, String content) {
        this.propertyId = propertyId;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

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
