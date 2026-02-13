package com.wherehouse.review.execptionHandler;

/**
 * 중복 리뷰 작성 예외
 *
 * 동일 사용자가 동일 매물에 이미 리뷰를 작성한 상태에서 재작성을 시도할 때 발생.
 * HTTP 응답: 409 Conflict
 */
public class ReviewDuplicateException extends ReviewException {

    public ReviewDuplicateException(String message) {
        super(message);
    }
}
