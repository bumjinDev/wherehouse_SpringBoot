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
     * 리뷰 목록 조회
     * * 변경 사항:
     * 1. Method: GET 유지
     * 2. Path: /list (작성 API와 구분)
     * 3. Param: @RequestBody -> @ModelAttribute (Query String 수신)
     */
    @GetMapping("/list")
    public ResponseEntity<ReviewListResponseDto> getReviews(
            @Valid @ModelAttribute ReviewListRequestDto requestDto) { // @RequestBody 제거

        log.info("리뷰 조회 요청: propertyId={}, page={}, sort={}, keyword={}",
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

        System.out.println(response.getReviewId());
        System.out.println(response.getPropertyId());
        System.out.println(response.getUserId());
        System.out.println(response.getRating());
        System.out.println(response.getContent());
        System.out.println(response.getTags());
        System.out.println(response.getCreatedAt());
        System.out.println(response.getUpdatedAt());


        log.info("리뷰 상세 조회 완료: reviewId={}", reviewId);

        return ResponseEntity.ok(response);
    }
}