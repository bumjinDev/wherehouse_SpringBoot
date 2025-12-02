package com.wherehouse.review.controller;

import com.wherehouse.review.dto.ReviewDetailDto;
import com.wherehouse.review.dto.ReviewListResponseDto;
import com.wherehouse.review.dto.ReviewQueryRequestDto;
import com.wherehouse.review.service.ReviewQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 리뷰 조회 API 컨트롤러
 *
 * 설계 명세서: 6.3 리뷰 목록 조회 API, 6.4 리뷰 단건 상세 조회 API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Validated
public class ReviewQueryController {

    private final ReviewQueryService reviewQueryService;

    /**
     * 리뷰 목록 조회 (통합)
     *
     * 설계 명세서: 6.3 리뷰 목록 조회 API (통합)
     *
     * @param requestDto 리뷰 조회 요청 DTO
     * @return 200 OK - 리뷰 목록 응답
     */
    @GetMapping("/query")
    public ResponseEntity<ReviewListResponseDto> getReviews(
            @Valid @RequestBody ReviewQueryRequestDto requestDto) {

        log.info("리뷰 조회 요청: propertyId={}, page={}, size={}, sort={}",
                requestDto.getPropertyId(),
                requestDto.getPage(),
                requestDto.getSize(),
                requestDto.getSort());

        ReviewListResponseDto response = reviewQueryService.getReviews(requestDto);

        log.info("리뷰 조회 완료: totalItems={}",
                response.getPagination().getTotalItems());

        return ResponseEntity.ok(response);
    }

    /**
     * 리뷰 단건 상세 조회
     *
     * 설계 명세서: 6.4 리뷰 단건 상세 조회 API
     *
     * @param reviewId 리뷰 ID
     * @return 200 OK - 리뷰 상세 정보
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewDetailDto> getReviewDetail(
            @PathVariable Long reviewId) {

        log.info("리뷰 상세 조회 요청: reviewId={}", reviewId);

        ReviewDetailDto response = reviewQueryService.getReviewDetail(reviewId);

        log.info("리뷰 상세 조회 완료: reviewId={}", reviewId);

        return ResponseEntity.ok(response);
    }
}