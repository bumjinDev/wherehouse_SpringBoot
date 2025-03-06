package com.wherehouse.board.service;

import java.security.Key;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.board.dao.BoardEntityRepository;
import com.wherehouse.board.dao.IBoardRepository;
import com.wherehouse.board.model.BoardConverter;
import com.wherehouse.board.model.BoardVO;
import com.wherehouse.board.model.CommandtVO;
import com.wherehouse.board.model.CommentConverter;
import com.wherehouse.exception.UnauthorizedException;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.members.model.MemberConverter;
import com.wherehouse.redis.handler.RedisHandler;

@Service
public class BoardService implements IBoardService {

    IBoardRepository boardRepository;
    
    IMembersRepository membersRepository;

    BoardEntityRepository boardEntityRepository;
    
    RedisHandler redisHandler;
    
    JWTUtil jwtUtil; // JWTUtil 의존성 주입
    
    CookieUtil cookieUtil;
    
    BoardConverter boardConverter;
    
    CommentConverter commentConverter;
    
    MemberConverter memberConverter;
    
    public BoardService(
    		
    		IBoardRepository boardRepository,
    		IMembersRepository membersRepository,
    		BoardEntityRepository boardEntityRepository,
    		
    		RedisHandler redisHandler,
    		JWTUtil jwtUtil,
    		CookieUtil cookieUtil,
    		
    		BoardConverter boardConverter,
    		CommentConverter commentConverter,
    		MemberConverter memberConverter
    	) {
    	
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
    
    // ======== 조회 관련 메소드 ========

    /**
     * 특정 페이지 번호에 해당하는 게시글 목록을 조회합니다.
     *
     * @param pnIndex 요청 페이지 번호
     * @return 해당 페이지의 게시글 목록과 관련 정보를 포함한 Map 객체
     */
    @Override
    public Map<String, Object> searchBoard(int pnIndex) {
        return boardRepository.searchBoardList(pnIndex *= 10); // 게시글 페이지 네이션으로써 보여지는 시작 지점.
    }

    /**
     * 게시글 번호 선택 시 게시글 및 관련 데이터를 조회하여 반환합니다.
     *
     * @param contentnum 선택한 게시글 번호
     * @return 게시글 내용, 댓글 목록, 작성자 닉네임 등을 포함한 Map 객체
     */
    @Override
    public Map<String, Object> sarchView(int contentnum) {
    	
        HashMap<String, Object> resultMap = new HashMap<>();
        
        // 조회수 1 증가
        boardRepository.upHit(contentnum);
        // 게시글 내용 조회 (실제로 없다면 "NoSuchElementException" 예외 발생)
        BoardVO boardVO = boardConverter.toVO(boardRepository.findBoard(contentnum));
        resultMap.put("content_view", boardVO);
        // 게시글 댓글 조회
        resultMap.put("comments", commentConverter.toVOList(boardRepository.commentSearch(contentnum)));
        // 게시글 작성자의 ID 로 회원 관리 테이블 내 닉네임 조회. (실제로 없다면 "NoSuchElementException" 예외 발생)
        resultMap.put("userName",  membersRepository.getMember(boardVO.getUserId()).getNickName());
        
        return resultMap;
    }

    // ======== 작성 관련 메소드 ========

    /**
     * 새 게시글 작성 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     */
    @Override
    public void boardWrite(BoardVO boardVO) {
	
    	boardVO.setBoardHit(0);
    	boardVO.setBoardDate(Date.valueOf(LocalDate.now())); // 현재 날짜를 bdate에 설정
    	
        boardRepository.boardWrite(boardConverter.toEntity(boardVO));
    }

    /**
     * 댓글 작성 요청을 처리합니다.
     * 요청 객체에서 댓글 내용을 추출하고 데이터베이스에 저장합니다.
     *
     * @param httpRequest 현재 요청 객체
     * 
     * 
     */
    @Override
    public void writeReply(String jwt, CommandtVO commandtVO) {
        boardRepository.replyWrite(commentConverter.toEntity(commandtVO));
    }

    // ======== 수정 관련 메소드 ========

    /**
     * 게시글 수정 페이지 제공 여부를 결정합니다.
     * 요청자의 ID와 게시글 작성자의 ID를 비교하여 페이지 반환을 결정합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @return 수정 가능 시 게시글 수정 페이지 경로, 불가능 시 알림 페이지 경로
     */
    public  HashMap<String, Object> boardModifyPage(String jwt, BoardVO boardVO) {
    	
        System.out.println("BoardModifyPageService.boardModifyPage()!");

        HashMap<String, Object> returnData = new HashMap<String, Object>();
       
        String sessionId = jwtUtil.extractUserId(
        								jwt,
        								jwtUtil.decodeBase64ToKey((String) redisHandler.getValueOperations().get(jwt)));

        if (sessionId.equals(boardVO.getUserId())) {
        	
        	returnData.put("boardId", String.valueOf(boardVO.getBoardId()));
        	returnData.put("title", boardVO.getTitle());
        	returnData.put("boardContent", boardVO.getBoardContent() );
        	returnData.put("region", boardVO.getRegion());
        	returnData.put("boardDate", String.valueOf(boardVO.getBoardDate()));
        	returnData.put("boardHit", String.valueOf(boardVO.getBoardHit()));
        	returnData.put("AuthorNickname", (String) membersRepository.getMember(sessionId).getNickName());
        	returnData.put("canModify", true);
        	
            return returnData;
            
        } else {
        	
        	returnData.put("canModify", false);

            return returnData;
        }
    }

    /**
     * 실제 글 수정 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     */
    @Override
    public void modifyBoard(BoardVO boardVO) {
        boardRepository.boardModify(boardConverter.toEntity(boardVO));
    }

    // ======== 삭제 관련 메소드 ========

    /**
     * 게시글 삭제 요청 처리 메소드.
     * 요청자의 ID와 작성자의 ID를 비교하여 삭제 가능 여부를 판단합니다.
     *
     * @param boardId 삭제 요청 게시글 ID
     * @param httpRequest 현재 요청 객체 (JWT 필터에서 설정된 userId 사용)
     * @return 삭제 성공 시 게시판 목록 페이지 URL, 실패 시 게시글 상세 페이지 URL
     */
    @Override
    public Map<String, String> deleteBoard(int boardId, String jwt) {
    	
        System.out.println("BoardDeleteService.deleteBoard()!");

        Map<String, String> response = new HashMap<>();
   
        String sessionId = jwtUtil.extractUserId(
        						jwt,
        						jwtUtil.decodeBase64ToKey((String) redisHandler.getValueOperations().get(jwt)));
        
        String boardWriterId = boardEntityRepository.findUseridByConnum(boardId);
        
        if (!sessionId.equals(boardWriterId)) {
            throw new UnauthorizedException("게시글 작성자가 아니므로 삭제할 수 없습니다.");
        }
        
        boardRepository.deleteBoard(boardId); // 실제 삭제 작업 수행

        return response;
    }

    /* 게시글 작성 페이지 내 필요한 "userId" "userName" 을 반환. */
	@Override
	public Map<String, String> writePage(String jwtToken) {
		
		Map<String, String> dataSet = new HashMap<String, String>();
		Key key = jwtUtil.decodeBase64ToKey((String) redisHandler.getValueOperations().get(jwtToken));

		dataSet.put("userId", jwtUtil.extractUserId(jwtToken, key));
		dataSet.put("userName", jwtUtil.extractUsername(jwtToken, key));
	
		return dataSet;
	}
}
