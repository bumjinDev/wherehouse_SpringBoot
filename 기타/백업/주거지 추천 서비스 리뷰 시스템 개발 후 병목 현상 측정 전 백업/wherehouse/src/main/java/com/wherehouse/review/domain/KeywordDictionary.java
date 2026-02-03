package com.wherehouse.review.domain;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 리뷰 키워드 사전
 *
 * 긍정/부정 키워드를 정적으로 정의하여 리뷰 텍스트 분석에 사용
 *
 * 설계 명세서: 8.2.2 처리 프로세스 - 사전 기반 매칭 (Dictionary Matching)
 */
@Getter
public enum KeywordDictionary {

    /**
     * 긍정 키워드
     *
     * 설계 명세서: 8.2.3 키워드 점수 저장 구조
     * SCORE: +1
     */
    POSITIVE(
            // 소음 관련
            "조용", "방음", "소음없", "조용한", "고요",

            // 채광 관련
            "밝다", "밝은", "채광", "남향", "햇빛", "해가잘", "일조량", "통풍",

            // 교통 관련
            "역세권", "가깝", "가까운", "도보", "평지", "접근성", "버스", "지하철역",

            // 편의시설 관련
            "편리", "편한", "편의점", "마트", "학교", "병원", "공원", "시장",

            // 관리 관련
            "깨끗", "관리좋", "친절", "신축", "리모델링", "수리잘",

            // 구조 관련
            "넓은", "넓다", "쾌적", "구조좋", "베란다"
    ),

    /**
     * 부정 키워드
     *
     * 설계 명세서: 8.2.3 키워드 점수 저장 구조
     * SCORE: -1
     */
    NEGATIVE(
            // 소음 관련
            "시끄럽", "소음", "층간소음", "소리", "떠들", "시끄러운", "소란",

            // 채광 관련
            "어둡", "어두운", "채광나쁨", "북향", "그늘", "습한", "곰팡이",

            // 교통 관련
            "멀다", "먼", "불편", "언덕", "경사", "접근불편", "교통불편", "외진",

            // 편의시설 관련
            "불편", "없다", "멀다", "부족", "불편한",

            // 관리 관련
            "노후", "낡은", "더럽", "관리안", "불친절", "고장", "파손",

            // 구조 관련
            "좁은", "좁다", "불편한구조", "베란다없"
    );

    private final List<String> keywords;

    KeywordDictionary(String... keywords) {
        this.keywords = Arrays.asList(keywords);
    }

    /**
     * 키워드 목록 조회
     *
     * @return 키워드 리스트
     */
    public List<String> getKeywords() {
        return keywords;
    }

    /**
     * 특정 키워드가 긍정인지 부정인지 판단
     *
     * @param keyword 키워드
     * @return +1 (긍정), -1 (부정), 0 (미등록)
     */
    public static int getScore(String keyword) {
        if (POSITIVE.keywords.contains(keyword)) {
            return 1;
        }
        if (NEGATIVE.keywords.contains(keyword)) {
            return -1;
        }
        return 0;
    }
}