package com.wherehouse.board.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/* 게시글 접근 권한 없음 (403) */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class BoardAuthorizationException extends RuntimeException {
    public BoardAuthorizationException(String message) {
        super(message);
    }
}
