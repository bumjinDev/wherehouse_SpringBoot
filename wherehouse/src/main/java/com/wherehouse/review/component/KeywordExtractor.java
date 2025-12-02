package com.wherehouse.review.component;

import com.wherehouse.review.domain.KeywordDictionary;
import com.wherehouse.review.domain.ReviewKeyword;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 키워드 자동 추출 컴포넌트
 *
 * 설계 명세서: 8.2 키워드 자동 추출 로직
 */
@Slf4j
@Component
public class KeywordExtractor {

    /**
     * 리뷰 텍스트에서 키워드 자동 추출
     *
     * 설계 명세서: 8.2.2 처리 프로세스
     *
     * @param reviewId 리뷰 ID
     * @param content 리뷰 텍스트 내용
     * @return 추출된 키워드 엔티티 리스트
     */
    public List<ReviewKeyword> extractKeywords(Long reviewId, String content) {

        List<ReviewKeyword> extractedKeywords = new ArrayList<>();

        Set<String> foundKeywords = new HashSet<>();  // 중복 방지

        // Step 1: 텍스트 정규화 (특수문자 제거, 소문자 변환 등)
        String normalizedContent = normalizeText(content);

        log.debug("키워드 추출 시작: reviewId={}, contentLength={}", reviewId, content.length());

        // Step 2: 긍정 키워드 매칭
        for (String keyword : KeywordDictionary.POSITIVE.getKeywords()) {
            if (normalizedContent.contains(keyword) && !foundKeywords.contains(keyword)) {
                ReviewKeyword reviewKeyword = ReviewKeyword.builder()
                        .reviewId(reviewId)
                        .keyword(keyword)
                        .score(1)  // 긍정: +1
                        .build();

                extractedKeywords.add(reviewKeyword);
                foundKeywords.add(keyword);

                log.debug("긍정 키워드 발견: keyword={}", keyword);
            }
        }

        // Step 3: 부정 키워드 매칭
        for (String keyword : KeywordDictionary.NEGATIVE.getKeywords()) {
            if (normalizedContent.contains(keyword) && !foundKeywords.contains(keyword)) {
                ReviewKeyword reviewKeyword = ReviewKeyword.builder()
                        .reviewId(reviewId)
                        .keyword(keyword)
                        .score(-1)  // 부정: -1
                        .build();

                extractedKeywords.add(reviewKeyword);
                foundKeywords.add(keyword);

                log.debug("부정 키워드 발견: keyword={}", keyword);
            }
        }

        log.info("키워드 추출 완료: reviewId={}, totalKeywords={}, positiveCount={}, negativeCount={}",
                reviewId,
                extractedKeywords.size(),
                extractedKeywords.stream().filter(k -> k.getScore() == 1).count(),
                extractedKeywords.stream().filter(k -> k.getScore() == -1).count());

        return extractedKeywords;
    }

    /**
     * 텍스트 정규화
     *
     * 설계 명세서: 8.2.2 처리 프로세스 - 1. 토크나이징 및 정규화
     *
     * @param text 원본 텍스트
     * @return 정규화된 텍스트
     */
    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        // 1. 소문자 변환 (한글은 영향 없음)
        String normalized = text.toLowerCase();

        // 2. 특수문자 제거 (공백은 유지)
        normalized = normalized.replaceAll("[^가-힣a-z0-9\\s]", "");

        // 3. 연속된 공백을 단일 공백으로
        normalized = normalized.replaceAll("\\s+", " ");

        // 4. 앞뒤 공백 제거
        normalized = normalized.trim();

        return normalized;
    }
}