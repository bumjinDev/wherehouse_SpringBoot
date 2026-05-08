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
@RequestMapping("/api/v1/reviews/properties")
@RequiredArgsConstructor
public class PropertySearchController {

    private final PropertySearchService propertySearchService;

    @GetMapping("/search")
    public ResponseEntity<List<PropertySearchResultDto>> searchProperties(
            @RequestParam String keyword) {

        log.info("매물 검색 요청: keyword={}", keyword);

        List<PropertySearchResultDto> results = propertySearchService.searchProperties(keyword);

        return ResponseEntity.ok(results);
    }
}
