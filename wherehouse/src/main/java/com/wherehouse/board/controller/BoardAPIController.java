package com.wherehouse.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.wherehouse.board.service.IBoardService;

@RestController
public class BoardAPIController {

    private final IBoardService boardService;

    public BoardAPIController(IBoardService boardService) {
        this.boardService = boardService;
    }

    /* 삭제 요청 API 는 권한 확인 과정과 그 실행 과정이 별도의 과정으로 구분되어 구현되지 않는다.
     * - 성공 : 204 번을 반환.
     * - 실패 : ExceptionHandler 에 의해 실패 메시지가 JAVASCRIPT 응답으로써 ResponseEntity<void> 반환. */
    @DeleteMapping("/delete/{boardId}")
    public ResponseEntity<Void> deleteBoard(@CookieValue(value = "Authorization", required = false) String jwt,
                                            @PathVariable("boardId") int boardId) {
        boardService.deleteBoard(boardId, jwt);
        return ResponseEntity.noContent().build(); // 204 No Content 반환, JS 단에서 별도의 에러 코드 없을 시 '/wherehouse/list/0' 로 분기
    }
    
    /* == 글 작성 페이지 요청 시 요청자와 게시글 작성자가 동일한지 결과를 반환 == */
}
