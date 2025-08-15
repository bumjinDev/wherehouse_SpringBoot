package com.wherehouse.board.service;

import java.security.Key;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.board.dao.BoardEntityRepository;
import com.wherehouse.board.dao.IBoardRepository;
import com.wherehouse.board.exception.*;
import com.wherehouse.board.model.*;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.model.*;
import com.wherehouse.redis.handler.RedisHandler;

/**
 * BoardService.java
 *
 * 게시글 도메인 서비스 클래스
 * - 게시글 등록, 조회, 수정, 삭제
 * - 댓글 작성 및 조회
 * - JWT 기반 인가 검증
 *
 * 실무 기준의 인증 분리 설계와 예외 처리를 포함합니다.
 */
@Service
public class BoardService implements IBoardService {

    private static final Logger logger = LoggerFactory.getLogger(BoardService.class);

    IBoardRepository boardRepository;
    IMembersRepository membersRepository;
    BoardEntityRepository boardEntityRepository;

    RedisHandler redisHandler;
    JWTUtil jwtUtil;
    CookieUtil cookieUtil;

    BoardConverter boardConverter;
    CommentConverter commentConverter;
    MemberConverter memberConverter;

    public BoardService(IBoardRepository boardRepository,
                        IMembersRepository membersRepository,
                        BoardEntityRepository boardEntityRepository,
                        RedisHandler redisHandler,
                        JWTUtil jwtUtil,
                        CookieUtil cookieUtil,
                        BoardConverter boardConverter,
                        CommentConverter commentConverter,
                        MemberConverter memberConverter) {
        this.boardRepository = boardRepository;
        this.membersRepository = membersRepository;
        this.boardEntityRepository = boardEntityRepository;
        this.redisHandler = redisHandler;
        this.jwtUtil = jwtUtil;
        this.cookieUtil = cookieUtil;
        this.boardConverter = boardConverter;
        this.commentConverter = commentConverter;
        this.memberConverter = memberConverter;
    }

    // ================== JWT 공통 처리 메서드 ==================

    /**
     * Purpose:
     *   JWT 토큰에서 Redis에 보관 중인 서명 키를 추출하여 복원.
     *
     * Flow:
     *   1. RedisHandler로 token 키를 조회
     *   2. JWTUtil로 Key 객체 변환 (orElseThrow)
     *
     * @param token JWT 문자열
     * @return 복원된 서명 Key
     */
    private Key extractSigningKey(String token) {

        return jwtUtil.getSigningKeyFromToken(token);
    }

    /**
     * Purpose:
     *   JWT 토큰에서 userId 클레임을 추출합니다.
     *
     * Flow:
     *   1. extractSigningKey → Key 복원
     *   2. jwtUtil.extractUserId → userId
     *
     * @param token JWT
     * @return userId (String)
     */
    private String extractUserIdFromToken(String token) {
        return jwtUtil.extractUserId(token);
    }

    /**
     * Purpose:
     *   JWT 토큰에서 사용자 닉네임(Username) 클레임을 추출합니다.
     *
     * Flow:
     *   1. extractSigningKey → Key 복원
     *   2. jwtUtil.extractUsername → userName
     *
     * @param token JWT
     * @return userName (String)
     */
    private String extractUserNameFromToken(String token) {
        return jwtUtil.extractUsername(token);
    }

    // ================== 게시글 목록 조회 ==================

    /**
     * Purpose:
     *   페이지 단위로 게시글 목록과 작성자 닉네임을 조회합니다.
     *
     * Flow:
     *   1. 전체 게시글 수로 페이지 수 계산
     *   2. pnIndex 기준 게시글 목록 조회
     *   3. 작성자 ID → 닉네임 매핑
     *   4. 결과 Map 반환
     *
     * @param pnIndex 페이지 인덱스 (0부터 시작)
     * @return Map: {pnSize, boardList, members}
     */
    @Override
    public Map<String, Object> listBoards(int pnIndex) {
        Map<String, Object> resultMap = new HashMap<>();

        // (1) 전체 게시글 수 조회 후 페이지 수 계산
        long totalCount = boardEntityRepository.count();
        int pnSize = (int) Math.ceil(totalCount / 10.0);

        // (2) 해당 페이지의 게시글 목록
        List<BoardEntity> boardList = boardRepository.findAllByPage(pnIndex);

        // (3) 작성자 ID 목록 → 닉네임 매핑
        List<String> userIds = boardList.stream()
                                        .map(BoardEntity::getUserid)
                                        .collect(Collectors.toList());
        Map<String, String> nickMap = membersRepository.getMembers(userIds).stream()
            .collect(Collectors.toMap(MembersEntity::getId, MembersEntity::getNickName));

        // (4) 게시글 순서대로 닉네임 리스트 구성
        List<String> members = boardList.stream()
            .map(be -> nickMap.getOrDefault(be.getUserid(), "Anonymous"))
            .collect(Collectors.toList());

        // 결과 저장
        resultMap.put("pnSize", pnSize);
        resultMap.put("boardList", boardList);
        resultMap.put("members", members);
        return resultMap;
    }

    // ================== 게시글 상세 조회 ==================

    /**
     * Purpose:
     *   게시글 상세 정보와 댓글을 조회하고, 조회수를 증가시킵니다.
     *   존재하지 않거나 조회수 증가 실패 시 예외 발생.
     *
     * Flow:
     *   1. 게시글 존재 검증(404) → DTO 변환
     *   2. 조회수 증가(실패 시 예외)
     *   3. 댓글 목록 + 작성자 닉네임 조회
     *   4. Map 반환
     *
     * @param boardId 게시글 PK
     * @return Map: content_view, comments, userName
     */
    @Override
    public Map<String, Object> getBoardDetail(int boardId) {
        Map<String, Object> resultMap = new HashMap<>();

        // (1) 게시글 조회(404) → DTO 변환
        BoardDTO boardDTO = boardConverter.toDTO(
            boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException("해당 게시글을 찾을 수 없습니다. ID: " + boardId)));

        // (2) 조회수 증가(실패 시 예외)
        if (boardRepository.incrementHitCount(boardId) == 0) {
            throw new BoardNotFoundException("게시글 조회수 증가 실패. ID: " + boardId);
        }

        // (3) 댓글 목록 + 작성자 닉네임 조회
        List<CommentVO> comments = commentConverter.toVOList(
            boardRepository.findCommentsByBoardId(boardId)
        );
        Optional<MembersEntity> memberOpt = membersRepository.getMember(boardDTO.getUserId());
        String userName = memberOpt.map(MembersEntity::getNickName).orElse("Anonymous");

        // (4) 결과 Map에 담기
        resultMap.put("content_view", boardDTO);
        resultMap.put("comments", comments);
        resultMap.put("userName", userName);

        return resultMap;
    }

    // ================== 게시글 작성 페이지 진입 ==================

    /**
     * Purpose:
     *   게시글 작성 페이지에 진입할 때, 사용자 식별자(ID)와 닉네임을 반환합니다.
     *
     * Flow:
     *   1. JWT에서 userId, userName 추출
     *   2. 결과 Map 반환
     *
     * @param token JWT 토큰
     * @return Map: {userId, userName}
     */
    @Override
    public Map<String, String> getBoardCreationInfo(String token) {
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("userId", extractUserIdFromToken(token));
        dataMap.put("userName", extractUserNameFromToken(token));
        return dataMap;
    }

    // ================== 게시글 신규 작성 ==================

    /**
     * Purpose:
     *   게시글 신규 작성 처리.
     *   서버에서 작성일, 조회수(0) 등을 강제 설정하고 생성된 ID를 반환합니다.
     *
     * Flow:
     *   1. DTO에 userId, boardHit=0, boardDate=오늘 날짜 설정
     *   2. DB에 게시글 저장 후 ID 추출
     *   3. HTTP 201 응답
     *
     * @param boardDTO 사용자 입력 DTO
     * @param token    JWT 토큰
     * @return { boardId }
     */
    @Override
    public String createBoard(BoardDTO boardDTO, String token) {
        // (1) DTO 필드 설정
        boardDTO.setUserId(extractUserIdFromToken(token));
        boardDTO.setBoardHit(0);
        boardDTO.setBoardDate(Date.valueOf(LocalDate.now()));

        // (2) DB 저장, 변환된 DTO에서 boardId 추출
        return String.valueOf(
            boardConverter.toDTO(boardRepository.createBoard(boardConverter.toEntity(boardDTO))).getBoardId());
    }

    // ================== 게시글 수정 페이지 진입 ==================

    /**
     * Purpose:
     *   게시글 수정 페이지 요청 처리.
     *   JWT 사용자 ID 인가 검증 후, 수정에 필요한 게시글 정보를 반환합니다.
     *
     * Flow:
     *   1. 로그 기록
     *   2. JWT 토큰에서 sessionId, userName 추출
     *   3. 게시글 조회(404) → DTO 변환
     *   4. 작성자 불일치 시 예외(403)
     *   5. 수정 페이지 데이터 맵 반환
     *
     * @param token   JWT 토큰
     * @param boardId 수정할 게시글 ID
     * @return 수정 페이지용 데이터
     */
    @Override
    public HashMap<String, String> getBoardForUpdate(String token, int boardId) {

        logger.info("BoardModifyPageService.boardModifyPage()! boardId={}", boardId);

        // (1) 사용자 정보 추출
        String sessionId = extractUserIdFromToken(token);
        String userName = extractUserNameFromToken(token);

        // (2) 게시글 존재 여부 검증
        BoardDTO boardDTO = boardConverter.toDTO(
            boardRepository.findById(boardId)
                .orElseThrow(() -> new InvalidBoardFoundAttemptException( "존재하지 않는 게시글에 임의적인 수정 페이지 요청 확인.")));

        // (3) 작성자 불일치 시 예외
        if (!boardDTO.getUserId().equals(sessionId)) {
            throw new InvalidBoardAccessAttemptException("게시글 작성자가 아닌 사용자의 임의적인 수정 접근.");
        }
        // (4) 수정 페이지용 데이터 구성
        HashMap<String, String> boardViewData = new HashMap<>();
        boardViewData.put("boardId", String.valueOf(boardDTO.getBoardId()));
        boardViewData.put("title", boardDTO.getTitle());
        boardViewData.put("boardContent", boardDTO.getBoardContent());
        boardViewData.put("region", boardDTO.getRegion());
        boardViewData.put("boardDate", String.valueOf(boardDTO.getBoardDate()));
        boardViewData.put("boardHit", String.valueOf(boardDTO.getBoardHit()));
        boardViewData.put("AuthorNickname", userName);

        return boardViewData;
    }

    // ================== 게시글 수정 ==================

    /**
     * Purpose:
     *   게시글 수정 처리.
     *   게시글 존재 및 작성자 검증 후, 변경된 필드를 DB에 반영합니다.
     *
     * Flow:
     *   1. 게시글 존재 여부 검증(404)
     *   2. 작성자 불일치 시 예외(403)
     *   3. boardDTO에 변경 요청 필드 적용
     *   4. DB에 업데이트 반영
     *
     * @param boardDTO 수정 요청 DTO
     * @param token    JWT 토큰
     */
    @Override
    public void updateBoard(BoardDTO boardDTO, String token) {

        // (1) 게시글 존재 여부 검증
        BoardDTO boardData = boardConverter.toDTO(
            boardRepository.findById(boardDTO.getBoardId())
                .orElseThrow(() -> new InvalidBoardFoundAttemptException("존재하지 않는 게시글에 임의적인 수정 페이지 요청 확인.")));

        // (2) 작성자 불일치 시 예외
        String sessionId = extractUserIdFromToken(token);
        if (!sessionId.equals(boardData.getUserId())) {
            throw new InvalidBoardAccessAttemptException("게시글 작성자가 아닌 사용자의 임의적인 수정 접근.");
        }

        // (3) 변경 필드 적용
        boardData.setTitle(boardDTO.getTitle());
        boardData.setRegion(boardDTO.getRegion());
        boardData.setBoardContent(boardDTO.getBoardContent());

        // (4) DB에 수정 반영
        boardRepository.updateBoard(boardConverter.toEntity(boardData));
    }

    // ================== 게시글 삭제 ==================

    /**
     * Purpose:
     *   게시글 삭제 요청 처리.
     *   JWT 사용자와 DB 게시글 작성자가 일치해야 함.
     *
     * Flow:
     *   1. 로그 기록
     *   2. JWT에서 userId 추출
     *   3. 게시글 존재 여부 검증(404)
     *   4. 작성자 불일치 시 예외(403)
     *   5. 게시글 삭제 후 204 응답
     *
     * @param boardId 게시글 ID
     * @param token   JWT 토큰
     * @return HTTP 204 No Content
     */
    @Override
    public void deleteBoard(int boardId, String token) {

        logger.info("BoardDeleteService.deleteBoard()! boardId={}", boardId);

        // (1) JWT에서 userId 추출
        String sessionId = extractUserIdFromToken(token);

        // (2) 게시글 존재 여부 검증(404)
        BoardDTO boardDTO = boardConverter.toDTO(
            boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException("선택하신 게시글은 이미 존재하지 않는 게시글 삭제 요청 입니다.")));

        // (3) 작성자 불일치 시 예외(403)
        if (!sessionId.equals(boardDTO.getUserId())) {
            throw new BoardAuthorizationException(   "선택하신 게시글에 대한 게시글 작성자가 아니므로 삭제할 수 없습니다.");
        }
        // (4) 게시글 삭제
        boardRepository.deleteBoard(boardId);
    }

    // ================== 댓글 작성 ==================

    /**
     * Purpose:
     *   댓글 작성 요청 처리.
     *   JWT 사용자 ID와 닉네임을 VO에 삽입 후 저장합니다.
     *
     * Flow:
     *   1. JWT 서명 키 복원
     *   2. 게시글 존재 여부 검증(404)
     *   3. 댓글 VO에 userId, userName 설정
     *   4. 댓글 저장
     *
     * @param token     JWT 토큰
     * @param commentVO 댓글 정보
     */
    @Override
    public void createReply(String token, CommentVO commentVO) {

        // (1) JWT 서명 키 복원
        Key signingKey = extractSigningKey(token);

        // (2) 게시글 존재 여부 검증(404)
        boardRepository.findById(commentVO.getBoardId())
            .orElseThrow(() -> new BoardNotFoundException("댓글 작성 요청 대상 게시글이 이미 삭제되어 게시글을 작성할 수 없습니다."));

        // (3) VO에 사용자 정보 설정
        commentVO.setUserId(jwtUtil.extractUserId(token));
        commentVO.setUserName(jwtUtil.extractUsername(token));

        // (4) 댓글 저장
        boardRepository.createComment(commentConverter.toEntity(commentVO));
    }
    // ================== 게시글 인가 검증 ==================

    /**
     * Purpose:
     *   게시글 수정/삭제 요청에 대한 인가를 검증합니다.
     *
     * Flow:
     *   1. JWT에서 userId 추출
     *   2. 게시글 존재 여부 검증(404)
     *   3. 작성자 불일치 시 예외(403)
     *   4. 정상 시 OK 반환
     *
     * @param token JWT 토큰
     * @param boardId 게시글 ID
     * @return HTTP 200 OK
     */
    @Override
    public ResponseEntity<Void> checkBoardAuthorization(String token, int boardId) {

        logger.info("BoardService.boardAuthorizationService()! boardId={}", boardId);

        // (1) JWT에서 userId 추출
        String userId = extractUserIdFromToken(token);

        // (2) 게시글 존재 여부 검증(404)
        BoardDTO boardDTO = boardConverter.toDTO(
            boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException("선택하신 게시글이 존재하지 않습니다.")));

        // (3) 작성자 불일치 시 예외(403)
        if (!boardDTO.getUserId().equals(userId)) {
            throw new BoardAuthorizationException("선택하신 게시글은 작성자만 접근 가능합니다.");
        }
        // (4) 정상 시 OK
        return ResponseEntity.ok().build();
    }
}
