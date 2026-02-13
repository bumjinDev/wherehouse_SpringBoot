package com.wherehouse.review.execptionHandler;

/**
 * 리뷰 접근 권한 예외
 *
 * 본인이 작성하지 않은 리뷰에 대해 수정/삭제를 시도할 때 발생.
 * HTTP 응답: 403 Forbidden
 */
public class ReviewAccessDeniedException extends ReviewException {

    public ReviewAccessDeniedException(String message) {
        super(message);
    }
}
