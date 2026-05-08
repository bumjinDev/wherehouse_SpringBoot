package com.wherehouse.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExtractedKeywordDto {
    private final String keyword;
    private final int score;
}
