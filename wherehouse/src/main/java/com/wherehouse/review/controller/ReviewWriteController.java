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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 리뷰 작성/수정/삭제 API 컨트롤러
 *
 * 설계 명세서: 6.2 리뷰 작성 API, 6.5 리뷰 수정 API, 6.6 리뷰 삭제 API
 */
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

        userId = "tes"; // Spring Secuirty Filter Chain 개발 전 임시 구현.

        // propertyId가 String으로 들어오므로 로그에서도 문자열로 출력됨
        log.info("리뷰 작성 요청: userId={}, propertyId={}", userId, requestDto.getPropertyId());

        ReviewCreateResponseDto response = reviewWriteService.createReview(requestDto, userId);

        log.info("리뷰 작성 완료: reviewId={}", response.getReviewId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * 리뷰 수정
     *
     * 설계 명세서: 6.5 리뷰 수정 API (재설계)
     *
     * 사용자가 본인이 작성한 리뷰를 수정한다.
     * 수정 시 키워드 재추출 및 통계 재계산이 수행된다.
     *
     * POST 방식에 맞춰 reviewId를 Request Body에 포함하여 처리
     *
     * @param requestDto 리뷰 수정 요청 (reviewId, rating, content)
     * @return 200 OK - 리뷰 수정 응답 (reviewId, updatedAt)
     */
    @PostMapping("/update")
    public ResponseEntity<ReviewUpdateResponseDto> updateReview(
            @Valid @RequestBody ReviewUpdateRequestDto requestDto ) {
            // @AuthenticationPrincipal String userId : Spring Seucurity 구현 시 실제 ID 값을 반영하여 준비된 실제 수정 메소드를 주석 해제 해서 추가 반영 필요.

        log.info("리뷰 수정 요청: reviewId={}, rating={}",
                requestDto.getReviewId(),
                requestDto.getRating());

        ReviewUpdateResponseDto response = reviewWriteService.updateReview(requestDto);

        log.info("리뷰 수정 완료: reviewId={}, updatedAt={}",
                response.getReviewId(),
                response.getUpdatedAt());

        return ResponseEntity.ok(response);
    }

    /**
     * 리뷰 삭제
     *
     * 설계 명세서: 6.6 리뷰 삭제 API
     *
     * 사용자가 본인이 작성한 리뷰를 삭제한다.
     * 삭제 시 해당 매물의 통계가 차감된다.
     *
     * @param reviewId 리뷰 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {

        log.info("리뷰 삭제 요청: reviewId={}", reviewId);

        reviewWriteService.deleteReview(reviewId);

        log.info("리뷰 삭제 완료: reviewId={}", reviewId);

        return ResponseEntity.noContent().build();
    }
}