package com.wherehouse.review.execptionHandler;

/**
 * XSS 입력 감지 예외
 *
 * 리뷰 본문에 HTML 태그 구조가 포함되어 있을 때 발생.
 * OWASP HTML Sanitizer가 유효한 HTML 태그를 감지한 경우.
 * HTTP 응답: 400 Bad Request
 */
public class ReviewXssException extends ReviewException {

    public ReviewXssException(String message) {
        super(message);
    }
}
