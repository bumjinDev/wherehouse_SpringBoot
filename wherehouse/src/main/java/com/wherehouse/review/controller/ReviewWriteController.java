package com.wherehouse.review.controller;

import com.wherehouse.review.dto.ReviewCreateRequestDto;
import com.wherehouse.review.dto.ReviewCreateResponseDto;
import com.wherehouse.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 리뷰 API 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Validated
public class ReviewWriteController {

    private final ReviewService reviewService;

    /**
     * 리뷰 작성
     *
     * @param requestDto 리뷰 작성 요청
     * @return 201 Created - 리뷰 작성 응답
     */
    @PostMapping
    public ResponseEntity<ReviewCreateResponseDto> createReview(
            @Valid @RequestBody ReviewCreateRequestDto requestDto) {

        log.info("리뷰 작성 요청: propertyId={}, userId={}, rating={}",
                requestDto.getPropertyId(),
                requestDto.getUserId(),
                requestDto.getRating());

        ReviewCreateResponseDto response = reviewService.createReview(requestDto);

        log.info("리뷰 작성 완료: reviewId={}", response.getReviewId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}