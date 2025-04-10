package com.wherehouse.board.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import com.wherehouse.board.model.BoardDTO;
import com.wherehouse.board.model.CommentVO;

/**
 * IBoardService
 *
 * 게시글과 댓글 관리를 위한 핵심 인터페이스입니다.
 * - 게시글 생성, 조회, 수정, 삭제
 * - 댓글 생성
 * - 권한(인가) 검증
 *
 * 실무 기준 예시: 
 * 필요한 정보를 간결하게 담고, 구현 로직은 구현체(Service)에서 주석 처리합니다.
 */
public interface IBoardService {

    /**
     * 게시글 상세 정보를 조회합니다.
     * 
     * 일반적으로 댓글 목록, 작성자 정보 등을 함께 반환할 수 있습니다.
     * 
     * @param boardId 조회할 게시글 식별자(PK)
     * @return 게시글 본문, 댓글 목록, 작성자 닉네임 등을 담은 Map
     * @throws BoardNotFoundException 게시글이 존재하지 않을 경우 (구현체에서 처리)
     */
    Map<String, Object> getBoardDetail(int boardId);

    /**
     * 게시글을 삭제합니다.
     * 
     * JWT 토큰을 통해 작성자 권한을 검증하고, DB에서 게시글을 삭제합니다.
     * 
     * @param boardId 삭제 대상 게시글 식별자(PK)
     * @param token   클라이언트로부터 전달받은 JWT 토큰
     * @return HTTP 204 No Content (구현체에서 상태 코드 설정)
     * @throws BoardNotFoundException      게시글이 이미 없는 경우
     * @throws BoardAuthorizationException 작성자가 아닐 경우
     */
    ResponseEntity<Void> deleteBoard(int boardId, String token);

    /**
     * 페이지 단위로 게시글 목록을 조회합니다.
     * 
     * 구현체에서 페이징 처리(예: 10개씩) 및 작성자 닉네임 매핑을 수행합니다.
     * 
     * @param pnIndex 0부터 시작하는 페이지 인덱스
     * @return pnSize(전체 페이지 수), boardList(게시글 목록), members(작성자 닉네임 목록) 등을 담은 Map
     */
    Map<String, Object> listBoards(int pnIndex);

    /**
     * 게시글 작성 페이지 진입 시 필요한 사용자 식별 정보(userId, userName 등)를 반환합니다.
     * 
     * @param token JWT 토큰
     * @return userId, userName 등을 담은 Map
     */
    Map<String, String> getBoardCreationInfo(String token);

    /**
     * 게시글 수정 페이지에 필요한 데이터를 제공합니다.
     * 
     * 구현체에서 게시글 존재 여부와 작성자 여부를 검증할 수 있습니다.
     * 
     * @param token   JWT 토큰
     * @param boardId 수정 대상 게시글 식별자
     * @return 수정 페이지에 필요한 게시글 정보(HashMap)
     * @throws InvalidBoardFoundAttemptException   게시글이 존재하지 않을 경우
     * @throws InvalidBoardAccessAttemptException  작성자가 아닐 경우
     */
    HashMap<String, String> getBoardForUpdate(String token, int boardId);

    /**
     * 게시글을 실제로 수정합니다.
     * 
     * 구현체에서 작성자 검증, DB 반영 등을 처리합니다.
     * 
     * @param boardVO 수정 요청 데이터
     * @param token   JWT 토큰
     * @throws InvalidBoardFoundAttemptException   게시글이 존재하지 않을 경우
     * @throws InvalidBoardAccessAttemptException  작성자가 아닐 경우
     */
    void updateBoard(BoardDTO boardVO, String token);

    /**
     * 댓글을 작성합니다.
     * 
     * JWT 토큰에서 userId, userName 추출 후 DB에 댓글을 등록합니다.
     * 
     * @param token      JWT 토큰
     * @param commandtVO 댓글 정보(게시글 ID, 댓글 본문 등)
     * @throws BoardNotFoundException 대상 게시글이 존재하지 않을 경우
     */
    void createReply(String token, CommentVO commandtVO);

    /**
     * 새 게시글을 생성합니다.
     * 
     * 작성일, 조회수(0) 등을 서버가 강제 설정할 수 있습니다.
     * 
     * @param boardVO 게시글 생성 데이터
     * @param token   JWT 토큰
     * @return { boardId }를 담은 JSON 응답 (HTTP 201)
     */
    ResponseEntity<Map<String, String>> createBoard(BoardDTO boardVO, String token);

    /**
     * 특정 게시글에 대한 접근 권한(수정, 삭제 등)을 사전에 검증합니다.
     * 
     * 게시글 존재 여부와 작성자 여부 등을 확인합니다.
     * 
     * @param token   JWT 토큰
     * @param boardId 게시글 식별자
     * @return HTTP 200 OK (인가 성공)
     * @throws BoardNotFoundException         게시글이 없을 경우
     * @throws BoardAuthorizationException    작성자가 아닐 경우
     */
    ResponseEntity<Void> checkBoardAuthorization(String token, int boardId);
}
