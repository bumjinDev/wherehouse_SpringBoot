package com.wherehouse.review.execptionHandler;

/**
 * 리뷰 미존재 예외
 *
 * 수정/삭제 대상 리뷰가 DB에 존재하지 않을 때 발생.
 * HTTP 응답: 404 Not Found
 */
public class ReviewNotFoundException extends ReviewException {

    public ReviewNotFoundException(String message) {
        super(message);
    }
}
