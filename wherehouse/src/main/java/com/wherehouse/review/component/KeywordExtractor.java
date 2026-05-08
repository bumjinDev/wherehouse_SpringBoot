package com.wherehouse.review.component;

import com.wherehouse.review.domain.KeywordDictionary;
import com.wherehouse.review.dto.ExtractedKeywordDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class KeywordExtractor {

    public List<ExtractedKeywordDto> extractKeywords(String content) {

        List<ExtractedKeywordDto> extractedKeywords = new ArrayList<>();
        Set<String> foundKeywords = new HashSet<>();

        String normalizedContent = normalizeText(content);

        for (String keyword : KeywordDictionary.POSITIVE.getKeywords()) {
            if (normalizedContent.contains(keyword) && !foundKeywords.contains(keyword)) {
                extractedKeywords.add(new ExtractedKeywordDto(keyword, 1));
                foundKeywords.add(keyword);
            }
        }

        for (String keyword : KeywordDictionary.NEGATIVE.getKeywords()) {
            if (normalizedContent.contains(keyword) && !foundKeywords.contains(keyword)) {
                extractedKeywords.add(new ExtractedKeywordDto(keyword, -1));
                foundKeywords.add(keyword);
            }
        }

        log.info("키워드 추출 완료: totalKeywords={}, positiveCount={}, negativeCount={}",
                extractedKeywords.size(),
                extractedKeywords.stream().filter(k -> k.getScore() > 0).count(),
                extractedKeywords.stream().filter(k -> k.getScore() < 0).count());

        return extractedKeywords;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.toLowerCase();
        normalized = normalized.replaceAll("[^가-힣a-z0-9\\s]", "");
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.trim();

        return normalized;
    }
}
