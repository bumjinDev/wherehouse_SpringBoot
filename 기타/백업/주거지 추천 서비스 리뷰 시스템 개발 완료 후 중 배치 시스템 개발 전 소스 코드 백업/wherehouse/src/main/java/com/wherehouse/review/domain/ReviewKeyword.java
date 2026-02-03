package com.wherehouse.review.domain;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

/**
 * 리뷰 키워드 엔티티
 *
 * 용도: 리뷰 텍스트에서 추출된 키워드 태그
 * Redis 매핑: 없음 (RDB 직접 조회)
 *
 * 설계 명세서: 7.1.2 C. REVIEW_KEYWORDS (리뷰 키워드)
 */
@Entity
@Table(name = "REVIEW_KEYWORDS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_keyword_id")
    @SequenceGenerator(name = "seq_keyword_id", sequenceName = "SEQ_KEYWORD_ID", allocationSize = 1)
    @Column(name = "KEYWORD_ID", nullable = false)
    private Long keywordId;

    /**
     * 참조 리뷰 ID
     * FK 제약조건: ON DELETE CASCADE
     */
    @Column(name = "REVIEW_ID", nullable = false)
    private Long reviewId;

    @Column(name = "KEYWORD", length = 50, nullable = false)
    private String keyword;

    /**
     * 감성 점수
     * 긍정: +1, 부정: -1
     *
     * 설계 명세서: 8.2.3 키워드 점수 저장 구조
     */
    @Column(name = "SCORE")
    private Integer score;

    @Builder
    public ReviewKeyword(Long reviewId, String keyword, Integer score) {
        this.reviewId = reviewId;
        this.keyword = keyword;
        this.score = score;
    }
}