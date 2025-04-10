package com.wherehouse.board.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wherehouse.board.model.BoardDTO;
import com.wherehouse.board.model.CommentVO;
import com.wherehouse.board.service.IBoardService;

import jakarta.validation.Valid;

/**
 * 게시판 기능에 대한 REST API 컨트롤러
 *
 * <p>게시글 작성, 수정, 삭제, 인가 확인, 댓글 등록 등의 JSON 기반 요청을 처리하며,
 * 응답은 ResponseEntity를 통해 HTTP 상태 코드를 중심으로 반환된다.</p>
 * 
 * <p>모든 URI는 "/boards" 하위 경로로 구성된다.</p>
 * 
 * @author -
 */
@RestController
@RequestMapping("/boards")
public class BoardResourceController {

    private static final Logger logger = LoggerFactory.getLogger(BoardResourceController.class);

    private final IBoardService boardService;

    public BoardResourceController(IBoardService boardService) {
        this.boardService = boardService;
    }

    /**
     * 게시글에 대한 인가 검증 요청
     *
     * <p>현재 사용자가 해당 게시글의 작성자인지 검증하며, 수정/삭제 요청 전에 호출됨.</p>
     *
     * @param Token    JWT 인증 토큰 (Authorization 쿠키)
     * @param boardId  인가 검증 대상 게시글 ID
     * @return 204 No Content (인가됨), 401/403 등 예외 발생 시 예외 핸들러 동작
     */
    @GetMapping("/{boardId}/auth")
    public ResponseEntity<Void> verifyBoardAuthorization(
            @CookieValue(value = "Authorization", required = false) String Token,
            @PathVariable("boardId") int boardId) {
        return boardService.checkBoardAuthorization(Token, boardId);
    }

    /**
     * 게시글 작성 요청
     *
     * @param token       JWT 인증 토큰 (Authorization 쿠키)
     * @param boardWrite  게시글 작성 데이터 (제목, 지역, 본문 등)
     * @return 작성 성공 시 userId 포함 Map, 실패 시 예외 발생
     * @throws MethodArgumentNotValidException 유효성 검증 실패 시 발생
     */
    @PostMapping("/")
    public ResponseEntity<Map<String, String>> writeBoard(
            @CookieValue(value = "Authorization", required = false) String token,
            @RequestBody @Valid BoardDTO boardWrite) {
        return boardService.createBoard(boardWrite, token);
    }

    /**
     * 게시글 수정 요청
     *
     * @param token   JWT 인증 토큰 (Authorization 쿠키)
     * @param boardVO 수정 대상 게시글 데이터
     * @return 204 No Content (수정 성공)
     */
    @PostMapping("/{boardId}")		// PathVariable : 글 작성과 수정 구분 용도
    public ResponseEntity<Void> modifyBoard(
            @CookieValue(value = "Authorization", required = false) String token,
            @RequestBody @Valid BoardDTO boardVO) {
        logger.info("BoardAPIController.modifyBoard()!");
        boardService.updateBoard(boardVO, token);
        return ResponseEntity.noContent().build();
    }

    /**
     * 게시글 삭제 요청
     *
     * @param token    JWT 인증 토큰 (Authorization 쿠키)
     * @param boardId  삭제할 게시글 ID
     * @return 204 No Content (삭제 성공), 실패 시 예외 발생
     */
    @DeleteMapping("/{boardId}")
    public ResponseEntity<Void> deleteBoard(
            @CookieValue(value = "Authorization", required = false) String token,
            @PathVariable("boardId") int boardId) {
        return boardService.deleteBoard(boardId, token);
    }

    /**
     * 댓글 등록 요청
     *
     * @param jwt         JWT 인증 토큰 (Authorization 쿠키)
     * @param commandtVO  댓글 데이터 (내용, 작성 대상 게시글 ID 등 포함)
     * @return 201 Created (댓글 등록 성공)
     */
    @PostMapping("/comments")
    public ResponseEntity<Void> writeReply(
            @CookieValue(value = "Authorization", required = false) String jwt,
            @RequestBody @Valid CommentVO commandtVO) {
        boardService.createReply(jwt, commandtVO);
        return ResponseEntity.status(201).build();
    }
}