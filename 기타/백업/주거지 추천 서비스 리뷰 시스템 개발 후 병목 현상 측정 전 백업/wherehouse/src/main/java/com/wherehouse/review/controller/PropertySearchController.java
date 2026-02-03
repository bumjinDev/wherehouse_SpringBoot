package com.wherehouse.review.controller;

import com.wherehouse.review.dto.PropertySearchResultDto;
import com.wherehouse.review.service.PropertySearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
public class PropertySearchController {

    private final PropertySearchService propertySearchService;

    /**
     * 매물 이름 검색 API (자동완성용)
     * * Endpoint: GET /api/v1/properties/search
     * Query Param: keyword (검색어)
     */
    @GetMapping("/search")
    public ResponseEntity<List<PropertySearchResultDto>> searchProperties(
            @RequestParam String keyword) {

        log.info("매물 검색 요청: keyword={}", keyword);

        List<PropertySearchResultDto> results = propertySearchService.searchProperties(keyword);

        return ResponseEntity.ok(results);
    }
}