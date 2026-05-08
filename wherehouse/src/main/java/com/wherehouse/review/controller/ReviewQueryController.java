package com.wherehouse.review.controller;

import com.wherehouse.review.dto.ReviewDetailDto;
import com.wherehouse.review.dto.ReviewListRequestDto;
import com.wherehouse.review.dto.ReviewListResponseDto;
import com.wherehouse.review.service.ReviewQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Slf4j
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Validated
public class ReviewQueryController {

    private final ReviewQueryService reviewQueryService;

    /**
     * 리뷰 목록 조회
     *
     * @param requestDto propertyType(필수), propertyId, propertyName, page, sort, keyword
     */
    @GetMapping("/list")
    public ResponseEntity<ReviewListResponseDto> getReviews(
            @Valid @ModelAttribute ReviewListRequestDto requestDto) {

        log.info("리뷰 조회 요청: propertyType={}, propertyId={}, page={}, sort={}, keyword={}",
                requestDto.getPropertyType(),
                requestDto.getPropertyId(),
                requestDto.getPage(),
                requestDto.getSort(),
                requestDto.getKeyword());

        ReviewListResponseDto response = reviewQueryService.getReviews(requestDto);

        return ResponseEntity.ok(response);
    }

    /**
     * 리뷰 단건 상세 조회
     *
     * @param reviewId 리뷰 ID
     * @param propertyType 매물 유형 ("charter" 또는 "monthly")
     */
    @GetMapping("/{reviewId}")
    public ResponseEntity<ReviewDetailDto> getReviewDetail(
            @PathVariable Long reviewId,
            @RequestParam
            @NotBlank(message = "매물 유형은 필수입니다")
            @Pattern(regexp = "^(charter|monthly)$", message = "매물 유형은 charter 또는 monthly만 가능합니다")
            String propertyType) {

        log.info("리뷰 상세 조회 요청: reviewId={}, propertyType={}", reviewId, propertyType);

        ReviewDetailDto response = reviewQueryService.getReviewDetail(reviewId, propertyType);

        return ResponseEntity.ok(response);
    }
}
