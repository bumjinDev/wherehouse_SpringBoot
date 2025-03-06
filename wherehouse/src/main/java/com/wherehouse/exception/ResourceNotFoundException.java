package com.wherehouse.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/*
 * RuntimeException을 상속한 예외 클래스는 특정 예외 상황을 명확히 정의하는 역할을 한다.
	즉, "이 예외가 발생하면 어떤 문제가 생긴 것인지 명확하게 표현"하는 용도로 사용된다.
 * */

/**
 * 404 NOT FOUND 예외 처리
 */
@ResponseStatus(HttpStatus.NOT_FOUND)		// 해당 예외가 발생했을 때 Spring 이 자동으로 HTTP RESPONSE 상태를 404 로 설정하도록 설정
public class ResourceNotFoundException extends RuntimeException{
	
	private static final long serialVersionUID = 1L;

	public ResourceNotFoundException(String message) {
        super(message);
    }
}
