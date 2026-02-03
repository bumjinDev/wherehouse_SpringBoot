package com.wherehouse.review.service;

import com.wherehouse.review.dto.PropertySearchResultDto;
import com.wherehouse.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertySearchService {

    private final ReviewRepository reviewRepository;

    /**
     * 매물 이름 자동완성 검색
     * @param keyword 사용자 입력 키워드
     * @return 검색 결과 DTO 리스트
     */
    public List<PropertySearchResultDto> searchProperties(String keyword) {
        if (keyword == null || keyword.trim().length() < 2) {
            return Collections.emptyList();
        }

        // Repository Native Query 호출
        List<Object[]> results = reviewRepository.searchPropertiesByName(keyword.trim());

        // Object[] -> DTO 매핑
        return results.stream()
                .map(row -> PropertySearchResultDto.builder()
                        .propertyId((String) row[0])
                        .propertyName((String) row[1])
                        .propertyType((String) row[2])
                        .build())
                .collect(Collectors.toList());
    }
}