package com.wherehouse.exception;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/* 현재 클래스의 하위에 위치한 모든 예외 들을 현재 클래스에서 공통으로 받아서 처리한다. */

@RestControllerAdvice
public class GlobalExceptionHandler {
	
	/**
     * 404 Not Found 예외 처리
     */
	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFoundException(ResourceNotFoundException ex, HttpServletRequest request) {
	    ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
	    problem.setTitle("Resource Not Found");		// 예외의 간단한 제목, 클라이언트가 어떤 종류의 오류인지 빠르게 확인.
	    problem.setDetail(ex.getMessage());			// 예외가 발생할 때 동적으로 생성된 메시지(예: "이 게시글을 삭제할 권한이 없음)
	    //problem.setType(URI.create("http://localhost:8185/wherehouse/not-found")); //  해당 예외 유형을 설명하는 문서 링크(URL) 지정.(예외를 설명하는 API 문서 위치)
	    problem.setInstance(URI.create(request.getRequestURI()));	// 예외가 발생한 요청 URL 제공.
	    return problem;
	}

	
	  /**
     * 403 Forbidden 예외 처리
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Unauthorized Access");
        problem.setDetail(ex.getMessage());
        //problem.setType(URI.create("http://localhost:8185/wherehouse/unauthorized"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /**
     * 500 Internal Server Error 예외 처리 (기타 모든 예외)
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Internal Server Error");
        problem.setDetail(ex.getMessage());
        //problem.setType(URI.create("http://localhost:8185/wherehouse/internal-server-error"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
