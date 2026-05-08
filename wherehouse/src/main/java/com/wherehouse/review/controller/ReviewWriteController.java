package com.wherehouse.review.controller;

import com.wherehouse.review.dto.ReviewCreateRequestDto;
import com.wherehouse.review.dto.ReviewCreateResponseDto;
import com.wherehouse.review.dto.ReviewUpdateRequestDto;
import com.wherehouse.review.dto.ReviewUpdateResponseDto;
import com.wherehouse.review.service.ReviewWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class ReviewWriteController {

    private final ReviewWriteService reviewWriteService;

    @PostMapping
    public ResponseEntity<ReviewCreateResponseDto> createReview(
            @Valid @RequestBody ReviewCreateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 작성 요청: userId={}, propertyId={}, propertyType={}",
                userId, requestDto.getPropertyId(), requestDto.getPropertyType());

        ReviewCreateResponseDto response = reviewWriteService.createReview(requestDto, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/update")
    public ResponseEntity<ReviewUpdateResponseDto> updateReview(
            @Valid @RequestBody ReviewUpdateRequestDto requestDto,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 수정 요청: userId={}, reviewId={}, propertyType={}",
                userId, requestDto.getReviewId(), requestDto.getPropertyType());

        ReviewUpdateResponseDto response = reviewWriteService.updateReview(requestDto, userId);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long reviewId,
            @RequestParam @NotBlank @Pattern(regexp = "^(charter|monthly)$") String propertyType,
            @AuthenticationPrincipal String userId) {

        log.info("리뷰 삭제 요청: userId={}, reviewId={}, propertyType={}", userId, reviewId, propertyType);

        reviewWriteService.deleteReview(reviewId, propertyType, userId);

        return ResponseEntity.noContent().build();
    }
}
