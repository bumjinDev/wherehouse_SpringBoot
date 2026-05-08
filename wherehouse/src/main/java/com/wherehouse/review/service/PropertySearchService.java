package com.wherehouse.review.service;

import com.wherehouse.review.dto.PropertySearchResultDto;
import com.wherehouse.review.repository.ReviewCharterRepository;
import com.wherehouse.review.repository.ReviewMonthlyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PropertySearchService {

    private final ReviewCharterRepository reviewCharterRepository;
    private final ReviewMonthlyRepository reviewMonthlyRepository;

    public List<PropertySearchResultDto> searchProperties(String keyword) {
        if (keyword == null || keyword.trim().length() < 2) {
            return Collections.emptyList();
        }

        String trimmed = keyword.trim();

        List<Object[]> charterResults = reviewCharterRepository.searchPropertiesByName(trimmed);
        List<Object[]> monthlyResults = reviewMonthlyRepository.searchPropertiesByName(trimmed);

        List<PropertySearchResultDto> results = new ArrayList<>(charterResults.size() + monthlyResults.size());

        for (Object[] row : charterResults) {
            results.add(toDto(row));
        }
        for (Object[] row : monthlyResults) {
            results.add(toDto(row));
        }

        return results;
    }

    private PropertySearchResultDto toDto(Object[] row) {
        return PropertySearchResultDto.builder()
                .propertyId((String) row[0])
                .propertyName((String) row[1])
                .propertyType((String) row[2])
                .build();
    }
}
