package com.wherehouse.review.controller; // 패키지명은 실제 프로젝트 구조에 맞게 수정

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(basePackages = "com.wherehouse.review")
public class GlobalExceptionHandlerReview {

    /**
     * IllegalStateException 처리
     * (예: "이미 해당 매물에 대한 리뷰를 작성하셨습니다")
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException e) {
        // 프론트엔드가 JSON으로 파싱할 수 있도록 Map 구성
        Map<String, String> response = new HashMap<>();
        response.put("message", e.getMessage()); // 예외 메시지 그대로 전달

        System.out.println("글 작성 중 에러 발생 : " + e.getMessage());

        // 409 Conflict: 리소스 상태 충돌 (중복 작성 등)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}