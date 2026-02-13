package com.wherehouse.review.execptionHandler;

/**
 * 리뷰 도메인 공통 부모 예외
 *
 * review 패키지 내에서 발생하는 모든 비즈니스 예외의 상위 클래스.
 * GlobalExceptionHandlerReview가 이 타입을 기반으로 예외를 분기 처리한다.
 */
public abstract class ReviewException extends RuntimeException {

    protected ReviewException(String message) {
        super(message);
    }
}
