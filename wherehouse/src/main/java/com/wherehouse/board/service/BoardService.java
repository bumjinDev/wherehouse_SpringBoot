package com.wherehouse.board.service;

import java.security.Key;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.wherehouse.JWT.Filter.Util.CookieUtil;
import com.wherehouse.JWT.Filter.Util.JWTUtil;
import com.wherehouse.board.dao.BoardEntityRepository;
import com.wherehouse.board.dao.IBoardRepository;
import com.wherehouse.board.model.BoardEntity;
import com.wherehouse.board.model.CommentEntity;
import com.wherehouse.members.dao.IMembersRepository;
import com.wherehouse.redis.handler.RedisHandler;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class BoardService implements IBoardService {

    @Autowired
    IBoardRepository boardRepository;
    
    @Autowired
    IMembersRepository membersRepository;

    @Autowired
    BoardEntityRepository boardEntityRepository;
    
    @Autowired
    RedisHandler redisHandler;
    
    @Autowired
    JWTUtil jwtUtil; // JWTUtil 의존성 주입
    
    @Autowired
    CookieUtil cookieUtil;
    
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

        // 게시글 내용 조회
        BoardEntity boardVO = boardRepository.findBoard(contentnum);
        resultMap.put("content_view", boardVO);

        // 게시글 댓글 조회
        resultMap.put("comments", boardRepository.commentSearch(contentnum));

        // 게시글 작성자 닉네임 조회 : BoardVO 는 작성자 ID 만 가지고 있으므로 이를 별도로 조회 해야 한다.
//        ArrayList<String> userId = new ArrayList<>();
//        userId.add(String.valueOf(boardVO.getUserid()));  
//        resultMap.put("AuthorNickname", boardRepository.getMembers(userId).get(0));
        resultMap.put("AuthorNickname",  membersRepository.getMember(boardVO.getUserid()).getNickName());
        
        return resultMap;
    }

    // ======== 작성 관련 메소드 ========

    /**
     * 새 게시글 작성 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     */
    @Override
    public void boardWrite(HttpServletRequest httpRequest) {
        BoardEntity boardEntity = new BoardEntity();

        boardEntity.setUserid(httpRequest.getParameter("userid"));
        boardEntity.setTitle(httpRequest.getParameter("title"));
        boardEntity.setBoardcontent(httpRequest.getParameter("boardcontent"));
        boardEntity.setRegion(httpRequest.getParameter("regions"));
        boardEntity.setBdate(Date.valueOf(LocalDate.now())); // 현재 날짜를 bdate에 설정
        
        System.out.println("boradEntity..getBdate() : " + boardEntity.getBdate());
        
        boardRepository.boardWrite(boardEntity);
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
    public void writeReply(HttpServletRequest httpRequest) {
    	
    	String JWT = cookieUtil.extractJwtFromCookies(httpRequest.getCookies(), "Authorization");
    	Key key = jwtUtil.decodeBase64ToKey(
    			(String) redisHandler.getValueOperations().get((JWT)));
    	
        CommentEntity comment = new CommentEntity();

        comment.setNum(Integer.parseInt(httpRequest.getParameter("boardId")));		// 게시글 번호 : 해당 댓글 작성되는 게시글 본문 번호를 기준으로 삼은 외래키 제약조건 위함.
        comment.setId(jwtUtil.extractUserId(JWT, key));					// 사용자 ID : membertbl 과의 제약 조건
        
        /* 실제 댓글로써 저장될 내용들. */
        comment.setNickname(jwtUtil.extractUsername(JWT, key));
        comment.setContent(httpRequest.getParameter("replyContent"));
        
        boardRepository.replyWrite(comment);
    }

    // ======== 수정 관련 메소드 ========

    /**
     * 게시글 수정 페이지 제공 여부를 결정합니다.
     * 요청자의 ID와 게시글 작성자의 ID를 비교하여 페이지 반환을 결정합니다.
     *
     * @param httpRequest 현재 요청 객체
     * @return 수정 가능 시 게시글 수정 페이지 경로, 불가능 시 알림 페이지 경로
     */
    public String boardModifyPage(HttpServletRequest httpRequest) {
        System.out.println("BoardModifyPageService.boardModifyPage()!");

        if (((String) httpRequest.getAttribute("userId")).equals((String) httpRequest.getParameter("writerId"))) {
            return "board/ContentEditPage";
        } else {
            return "board/ContentAlertPage";
        }
    }

    /**
     * 실제 글 수정 요청을 처리합니다.
     *
     * @param httpRequest 현재 요청 객체
     */
    @Override
    public void modifyBoard(HttpServletRequest httpRequest) {
        BoardEntity boardEntity = new BoardEntity();

        boardEntity.setConnum(Integer.parseInt(httpRequest.getParameter("boardId")));
        boardEntity.setTitle(httpRequest.getParameter("title"));
        boardEntity.setBoardcontent(httpRequest.getParameter("boardContent"));
        boardEntity.setRegion(httpRequest.getParameter("regions"));

        boardRepository.boardModify(boardEntity);
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
    public String deleteBoard(String boardId, HttpServletRequest httpRequest) {
        System.out.println("BoardDeleteService.deleteBoard()!");

        if (boardEntityRepository.findUseridByConnum(Integer.parseInt(boardId))
                .equals((String) httpRequest.getAttribute("userId"))) {
            boardRepository.deleteBoard(Integer.parseInt(boardId));
            return "/wherehouse/list/0";
        } else {
            return "/wherehouse/choiceboard/" + boardId;
        }
    }
}
